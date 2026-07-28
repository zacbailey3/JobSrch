package com.jobsrch.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.ResumeAnalysisProperties;

import jakarta.annotation.PreDestroy;

/**
 * Converts supported resumes using bounded parser workers, time, pages, text,
 * ZIP expansion, and PDF scratch storage.
 */
@Service
public class ResumeTextExtractor {

    private static final long MAX_PDF_SCRATCH_BYTES = 50L * 1024 * 1024;
    private static final long MAX_DOCX_ENTRY_BYTES = 20L * 1024 * 1024;
    private static final long MAX_DOCX_TEXT_BYTES = 1024L * 1024;
    private static final long MAX_DOCX_FILES = 200;

    private final ResumeAnalysisProperties properties;
    private final ThreadPoolExecutor parserExecutor;

    public ResumeTextExtractor(ResumeAnalysisProperties properties) {
        this.properties = properties;
        this.parserExecutor = new ThreadPoolExecutor(
                properties.workerCount(),
                properties.workerCount(),
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "resume-parser");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public String extract(Path file, String contentType) {
        Future<String> future;
        try {
            future = parserExecutor.submit(() -> extractSafely(file, contentType));
        } catch (RejectedExecutionException exception) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Resume analysis is busy. Please try again shortly.",
                    exception);
        }

        try {
            return future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ResponseStatusException(
                    HttpStatus.REQUEST_TIMEOUT,
                    "Resume analysis exceeded the safe processing time",
                    exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Resume analysis was interrupted",
                    exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof ResponseStatusException responseStatus) {
                throw responseStatus;
            }
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Resume text could not be extracted",
                    exception.getCause());
        }
    }

    private String extractSafely(Path file, String contentType) {
        try {
            String text = switch (contentType) {
                case "application/pdf" -> extractPdf(file);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        extractDocx(file);
                default -> throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Resume format cannot be analyzed");
            };
            return normalizeAndLimit(text);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Resume text could not be extracted",
                    exception);
        }
    }

    private String extractPdf(Path file) throws IOException {
        MemoryUsageSetting scratch = MemoryUsageSetting.setupTempFileOnly(MAX_PDF_SCRATCH_BYTES);
        try (PDDocument document = Loader.loadPDF(file.toFile(), scratch.streamCache)) {
            if (document.isEncrypted()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Password-protected resumes cannot be analyzed");
            }
            if (document.getNumberOfPages() > properties.maxPdfPages()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Resume exceeds the maximum PDF page count");
            }

            LimitedWriter output = new LimitedWriter(properties.maxTextLength());
            try {
                new PDFTextStripper().writeText(document, output);
            } catch (TextLimitReachedException ignored) {
                // Enough text has been collected; stop parsing additional content.
            }
            return output.toString();
        }
    }

    private String extractDocx(Path file) throws IOException {
        configurePoiZipLimits();
        try (InputStream input = Files.newInputStream(file);
                XWPFDocument document = new XWPFDocument(input);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private void configurePoiZipLimits() {
        ZipSecureFile.setMinInflateRatio(0.02);
        ZipSecureFile.setMaxEntrySize(MAX_DOCX_ENTRY_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_DOCX_TEXT_BYTES);
        ZipSecureFile.setMaxFileCount(MAX_DOCX_FILES);
    }

    private String normalizeAndLimit(String text) {
        String normalized = text.replace('\u0000', ' ').trim();
        return normalized.length() <= properties.maxTextLength()
                ? normalized
                : normalized.substring(0, properties.maxTextLength());
    }

    @PreDestroy
    void shutdown() {
        parserExecutor.shutdownNow();
    }

    private static final class LimitedWriter extends Writer {

        private final int limit;
        private final StringBuilder value = new StringBuilder();

        private LimitedWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            int remaining = limit - value.length();
            if (remaining <= 0) {
                throw new TextLimitReachedException();
            }
            int accepted = Math.min(length, remaining);
            value.append(chars, offset, accepted);
            if (accepted < length) {
                throw new TextLimitReachedException();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    private static final class TextLimitReachedException extends IOException {
    }
}
