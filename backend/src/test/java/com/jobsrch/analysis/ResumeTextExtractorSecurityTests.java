package com.jobsrch.analysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.ResumeAnalysisProperties;

class ResumeTextExtractorSecurityTests {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsPdfBeyondConfiguredPageLimit() throws IOException {
        Path pdf = tempDirectory.resolve("too-many-pages.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        ResumeTextExtractor extractor = new ResumeTextExtractor(
                new ResumeAnalysisProperties(
                        Duration.ofSeconds(5),
                        1,
                        1,
                        1,
                        10_000));
        try {
            assertThatThrownBy(() -> extractor.extract(pdf, "application/pdf"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("page count");
        } finally {
            extractor.shutdown();
        }
    }
}
