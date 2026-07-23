package com.jobsrch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Instant;

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
import com.jobsrch.alert.SavedSearchAlertService;
import com.jobsrch.alert.SavedSearchRequest;
import com.jobsrch.auth.AuthResponse;
import com.jobsrch.auth.AuthService;
import com.jobsrch.auth.AccountDeletionService;
import com.jobsrch.auth.DeleteAccountRequest;
import com.jobsrch.auth.LoginRequest;
import com.jobsrch.auth.PasswordResetConfirmRequest;
import com.jobsrch.auth.PasswordResetRequest;
import com.jobsrch.auth.PasswordResetResponse;
import com.jobsrch.auth.PasswordResetService;
import com.jobsrch.auth.RegisterRequest;
import com.jobsrch.job.JobRequest;
import com.jobsrch.job.JobResponse;
import com.jobsrch.job.JobService;
import com.jobsrch.discovery.DiscoveredJob;
import com.jobsrch.discovery.CareerStage;
import com.jobsrch.discovery.DegreeRequirement;
import com.jobsrch.discovery.IndexedJobRepository;
import com.jobsrch.discovery.JobIndexService;
import com.jobsrch.discovery.JobProvider;
import com.jobsrch.discovery.OpportunityType;
import com.jobsrch.discovery.SponsorshipStatus;
import com.jobsrch.discovery.WorkplaceType;
import com.jobsrch.profile.ProfileRequest;
import com.jobsrch.profile.ProfileResponse;
import com.jobsrch.profile.ProfileService;
import com.jobsrch.resume.ResumeResponse;
import com.jobsrch.resume.ResumeService;
import com.jobsrch.user.UserAccountRepository;

@SpringBootTest
@Transactional
class CoreWorkflowTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordResetService passwordResetService;

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

    @Autowired
    private SavedSearchAlertService savedSearchAlertService;

    @Autowired
    private IndexedJobRepository indexedJobs;

    @Autowired
    private JobIndexService jobIndexService;

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private UserAccountRepository users;

    @Test
    void userCanPermanentlyDeleteAccountAndOwnedData() {
        AuthResponse auth = authService.register(new RegisterRequest(
                "delete@example.com",
                "strong-password",
                "Delete",
                "Me"));
        Jwt jwt = jwtDecoder.decode(auth.accessToken());
        jobService.create(jwt, new JobRequest(
                "Acme", "Junior Developer", "Remote", "Java role",
                "https://example.com/delete", 0, 2, null));
        resumeService.upload(jwt, new MockMultipartFile(
                "file", "delete.pdf", "application/pdf", "%PDF-1.4 sample".getBytes()));

        accountDeletionService.delete(jwt, new DeleteAccountRequest("strong-password"));

        assertThat(users.findByEmailIgnoreCase("delete@example.com")).isEmpty();
    }

    @Test
    void userCanResetPasswordWithAOneTimeToken() {
        authService.register(new RegisterRequest(
                "reset@example.com",
                "old-password",
                "Reset",
                "User"));

        PasswordResetResponse reset = passwordResetService.requestReset(
                new PasswordResetRequest("reset@example.com"));
        assertThat(reset.developmentResetToken()).isNotBlank();

        passwordResetService.resetPassword(new PasswordResetConfirmRequest(
                reset.developmentResetToken(),
                "new-password"));

        assertThat(authService.login(new LoginRequest(
                "reset@example.com",
                "new-password")).accessToken()).isNotBlank();
        assertThatThrownBy(() -> passwordResetService.resetPassword(
                new PasswordResetConfirmRequest(
                        reset.developmentResetToken(),
                        "another-password")))
                .hasMessageContaining("invalid or has expired");
    }

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

    @Test
    void savedSearchReceivesJobsAddedByALaterImport() {
        AuthResponse auth = authService.register(new RegisterRequest(
                "alerts@example.com",
                "strong-password",
                "Alert",
                "Owner"));
        Jwt jwt = jwtDecoder.decode(auth.accessToken());
        var search = savedSearchAlertService.create(jwt, new SavedSearchRequest(
                "Remote Java",
                "java developer",
                "Remote",
                "US",
                WorkplaceType.REMOTE,
                30,
                true,
                OpportunityType.FULL_TIME,
                CareerStage.ENTRY_LEVEL,
                DegreeRequirement.NOT_STATED,
                SponsorshipStatus.NOT_STATED,
                2,
                true));

        DiscoveredJob discovered = new DiscoveredJob(
                "alert-1",
                JobProvider.ADZUNA,
                "Northstar",
                "Junior Java Developer",
                "Remote, US",
                "US",
                WorkplaceType.REMOTE,
                "This is a full-time role building Java services.",
                "https://example.com/jobs/alert-1",
                Instant.now(),
                null,
                0,
                2,
                true);
        jobIndexService.upsertAll(java.util.List.of(discovered));

        savedSearchAlertService.refreshAll();

        assertThat(savedSearchAlertService.list(jwt))
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.id()).isEqualTo(search.id());
                    assertThat(saved.name()).isEqualTo("Remote Java");
                    assertThat(saved.lastCheckedAt()).isAfter(search.lastCheckedAt());
                });
        assertThat(savedSearchAlertService.listAlerts(jwt))
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.savedSearchName()).isEqualTo("Remote Java");
                    assertThat(alert.job().title()).isEqualTo("Junior Java Developer");
                    assertThat(alert.seen()).isFalse();
                });
        savedSearchAlertService.markAllSeen(jwt);
        assertThat(savedSearchAlertService.listAlerts(jwt).get(0).seen()).isTrue();
    }

    @Test
    void providerLocationLongerThanLegacyLimitCanBeIndexed() {
        String longLocation = "Atlanta, Georgia; Bellevue, Washington; "
                + "Boston, Massachusetts; Maryland; Philadelphia, Pennsylvania; "
                + "San Francisco, California; Seattle, Washington; "
                + "Washington, District of Columbia; Remote, United States";
        DiscoveredJob discovered = new DiscoveredJob(
                "long-location",
                JobProvider.GREENHOUSE,
                "Example",
                "Junior Software Engineer",
                longLocation,
                "US",
                WorkplaceType.HYBRID,
                "Build software",
                "https://example.com/jobs/long-location",
                Instant.now(),
                null,
                0,
                2,
                true);

        jobIndexService.upsertAll(java.util.List.of(discovered));

        assertThat(indexedJobs.findByActiveTrue())
                .anySatisfy(job -> assertThat(job.getLocation()).isEqualTo(longLocation));
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
