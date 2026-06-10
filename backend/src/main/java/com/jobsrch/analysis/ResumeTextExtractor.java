package com.jobsrch.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Converts supported resume formats into plain text for local analysis.
 *
 * <p>Format-specific libraries stay in this class so the matching algorithm
 * has no dependency on PDF or Office document APIs.</p>
 */
@Service
public class ResumeTextExtractor {

    private static final int MAX_TEXT_LENGTH = 200_000;

    public String extract(Path file, String contentType) {
        try {
            String text = switch (contentType) {
                case "application/pdf" -> extractPdf(file);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        extractDocx(file);
                default -> throw new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Resume format cannot be analyzed");
            };
            String normalized = text.replace('\u0000', ' ').trim();
            return normalized.length() <= MAX_TEXT_LENGTH
                    ? normalized
                    : normalized.substring(0, MAX_TEXT_LENGTH);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Resume text could not be extracted",
                    exception);
        }
    }

    private String extractPdf(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file);
                XWPFDocument document = new XWPFDocument(input);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
