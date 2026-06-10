package com.jobsrch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.jobsrch.application.ApplicationRequest;
import com.jobsrch.application.ApplicationResponse;
import com.jobsrch.application.ApplicationService;
import com.jobsrch.application.ApplicationStatus;
import com.jobsrch.analysis.AnalysisRequest;
import com.jobsrch.analysis.AnalysisResponse;
import com.jobsrch.analysis.ResumeAnalysisService;
import com.jobsrch.auth.AuthResponse;
import com.jobsrch.auth.AuthService;
import com.jobsrch.auth.RegisterRequest;
import com.jobsrch.job.JobRequest;
import com.jobsrch.job.JobResponse;
import com.jobsrch.job.JobService;
import com.jobsrch.profile.ProfileRequest;
import com.jobsrch.profile.ProfileResponse;
import com.jobsrch.profile.ProfileService;
import com.jobsrch.resume.ResumeResponse;
import com.jobsrch.resume.ResumeService;

@SpringBootTest
@Transactional
class CoreWorkflowTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JobService jobService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeAnalysisService analysisService;

    @Test
    void userCanRegisterSaveAJobAndTrackAnApplication() {
        AuthResponse auth = authService.register(new RegisterRequest(
                "new.grad@example.com",
                "strong-password",
                "New",
                "Graduate"));
        Jwt jwt = jwtDecoder.decode(auth.accessToken());

        JobResponse job = jobService.create(jwt, new JobRequest(
                "Acme",
                "Junior Java Developer",
                "Remote",
                "Build APIs with Java and Spring.",
                "https://example.com/jobs/1",
                0,
                2,
                null));

        ApplicationResponse application = applicationService.create(jwt, new ApplicationRequest(
                job.id(),
                job.company(),
                job.title(),
                job.sourceUrl(),
                ApplicationStatus.APPLIED,
                LocalDate.now(),
                "Applied through the company site."));

        assertThat(jobService.list(jwt, "java")).containsExactly(job);
        assertThat(application.jobPostingId()).isEqualTo(job.id());
        assertThat(application.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(applicationService.list(jwt)).containsExactly(application);
    }

    @Test
    void userCanMaintainAProfileAndResumeLibrary() {
        AuthResponse auth = authService.register(new RegisterRequest(
                "profile@example.com",
                "strong-password",
                "Profile",
                "Owner"));
        Jwt jwt = jwtDecoder.decode(auth.accessToken());

        ProfileResponse profile = profileService.update(jwt, new ProfileRequest(
                "555-0100",
                "Seattle, WA",
                "New graduate Java developer",
                "B.S. Computer Science",
                2026,
                1,
                "Backend developer, software engineer",
                "Java, Spring Boot, SQL",
                "https://linkedin.com/in/example",
                "https://example.dev"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                "%PDF-1.4 sample".getBytes());
        ResumeResponse resume = resumeService.upload(jwt, file);

        assertThat(profileService.get(jwt)).isEqualTo(profile);
        assertThat(resumeService.list(jwt)).containsExactly(resume);

        resumeService.delete(jwt, resume.id());
        assertThat(resumeService.list(jwt)).isEmpty();
    }

    @Test
    void userReceivesExplainableResumeToJobSuggestions() throws IOException {
        AuthResponse auth = authService.register(new RegisterRequest(
                "analysis@example.com",
                "strong-password",
                "Analysis",
                "Owner"));
        Jwt jwt = jwtDecoder.decode(auth.accessToken());
        JobResponse job = jobService.create(jwt, new JobRequest(
                "Northstar",
                "Junior Java Developer",
                "Remote",
                "Build REST APIs with Java, Spring Boot, SQL, Git, and Docker.",
                "https://example.com/jobs/analysis",
                1,
                3,
                null));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analysis-resume.pdf",
                "application/pdf",
                samplePdf("""
                        ANALYSIS OWNER
                        analysis@example.com 555-123-4567
                        SKILLS
                        Java, SQL, Git
                        EXPERIENCE
                        1 year building REST APIs for a capstone project.
                        EDUCATION
                        B.S. Computer Science
                        """));
        ResumeResponse resume = resumeService.upload(jwt, file);

        AnalysisResponse analysis = analysisService.analyze(
                jwt, new AnalysisRequest(resume.id(), job.id()));

        assertThat(analysis.matchedSkills()).contains("Java", "REST APIs", "SQL", "Git");
        assertThat(analysis.missingSkills()).contains("Spring Boot", "Docker");
        assertThat(analysis.overallScore()).isBetween(1, 99);
        assertThat(analysis.suggestions()).anyMatch(suggestion -> suggestion.contains("Spring Boot"));

        resumeService.delete(jwt, resume.id());
    }

    private byte[] samplePdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(14);
                content.newLineAtOffset(50, 750);
                for (String line : text.lines().toList()) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
