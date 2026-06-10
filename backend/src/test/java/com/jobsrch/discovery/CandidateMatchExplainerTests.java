package com.jobsrch.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jobsrch.profile.ProfileResponse;

class CandidateMatchExplainerTests {

    private final CandidateMatchExplainer explainer = new CandidateMatchExplainer();

    @Test
    void addsProfileSkillAndExperienceEvidence() {
        DiscoveredJob job = new DiscoveredJob(
                "job-1",
                JobProvider.LEVER,
                "Example",
                "Junior Java Developer",
                "Remote, US",
                "US",
                WorkplaceType.REMOTE,
                "Build Java and SQL services.",
                "https://example.com/job-1",
                Instant.now(),
                null,
                0,
                2,
                true,
                OpportunityType.FULL_TIME,
                CareerStage.ENTRY_LEVEL,
                DegreeRequirement.NOT_STATED,
                SponsorshipStatus.NOT_STATED,
                Instant.now(),
                List.of("The title explicitly uses a junior or entry-level label."),
                List.of("Visa sponsorship is not specified."));
        ProfileResponse profile = new ProfileResponse(
                null,
                "Chicago",
                "New graduate developer",
                "B.S. Computer Science",
                2026,
                1,
                "Java Developer",
                "Java, SQL, Docker",
                null,
                null,
                Instant.now());

        DiscoveredJob explained = explainer.explain(job, profile);

        assertThat(explained.matchReasons())
                .anyMatch(reason -> reason.contains("Java, SQL"))
                .anyMatch(reason -> reason.contains("1 year of experience"));
    }
}
