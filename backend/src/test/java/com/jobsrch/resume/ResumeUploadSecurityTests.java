package com.jobsrch.resume;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.MalwareScanProperties;
import com.jobsrch.config.StorageProperties;

class ResumeUploadSecurityTests {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsDocxArchivesWithTooManyEntries() throws IOException {
        ResumeStorageService storage = new ResumeStorageService(
                new StorageProperties(tempDirectory),
                new ResumeMalwareScanner(disabledScannerProperties()));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "oversized.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithEntries(201));

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("valid PDF or DOCX");
    }

    @Test
    void disabledScannerMakesLocalDevelopmentIndependentOfClamAv() {
        ResumeMalwareScanner scanner = new ResumeMalwareScanner(disabledScannerProperties());
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4".getBytes());

        assertThatCode(() -> scanner.scan(file)).doesNotThrowAnyException();
    }

    @Test
    void enabledScannerFailsClosedWhenClamAvIsUnavailable() throws IOException {
        int unavailablePort;
        try (ServerSocket reservation = new ServerSocket(0)) {
            unavailablePort = reservation.getLocalPort();
        }
        ResumeMalwareScanner scanner = new ResumeMalwareScanner(new MalwareScanProperties(
                true,
                "127.0.0.1",
                unavailablePort,
                Duration.ofMillis(200),
                Duration.ofMillis(200)));
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-1.4".getBytes());

        assertThatThrownBy(() -> scanner.scan(file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    private MalwareScanProperties disabledScannerProperties() {
        return new MalwareScanProperties(
                false,
                "localhost",
                3310,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
    }

    private byte[] docxWithEntries(int count) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index < count; index++) {
                String name = switch (index) {
                    case 0 -> "[Content_Types].xml";
                    case 1 -> "word/document.xml";
                    default -> "word/entry-" + index + ".xml";
                };
                zip.putNextEntry(new ZipEntry(name));
                zip.write("<xml/>".getBytes());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }
}
