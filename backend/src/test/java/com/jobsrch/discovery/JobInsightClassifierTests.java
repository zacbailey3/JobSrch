package com.jobsrch.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class JobInsightClassifierTests {

    private final JobInsightClassifier classifier = new JobInsightClassifier();

    @Test
    void identifiesNewGradDegreeAndSponsorshipSignals() {
        DiscoveredJob result = classifier.enrich(job(
                "New Grad Software Engineer",
                """
                This is a full-time graduate program for recent graduates.
                A bachelor's degree or equivalent experience is accepted.
                Visa sponsorship is available. Candidates need 0-2 years of experience.
                """,
                0,
                2));

        assertThat(result.opportunityType()).isEqualTo(OpportunityType.FULL_TIME);
        assertThat(result.careerStage()).isEqualTo(CareerStage.NEW_GRAD);
        assertThat(result.degreeRequirement()).isEqualTo(DegreeRequirement.NO_DEGREE_REQUIRED);
        assertThat(result.sponsorshipStatus()).isEqualTo(SponsorshipStatus.AVAILABLE);
        assertThat(result.matchReasons()).anyMatch(reason -> reason.contains("recent graduates"));
        assertThat(result.verifiedAt()).isNotNull();
    }

    @Test
    void exposesRestrictionsAsCautionsInsteadOfHidingThem() {
        DiscoveredJob result = classifier.enrich(job(
                "Junior Data Analyst",
                """
                A bachelor's degree is required. Applicants must be authorized to work
                in the United States without current or future visa sponsorship.
                """,
                null,
                null));

        assertThat(result.degreeRequirement()).isEqualTo(DegreeRequirement.BACHELORS_REQUIRED);
        assertThat(result.sponsorshipStatus()).isEqualTo(SponsorshipStatus.NOT_AVAILABLE);
        assertThat(result.cautions())
                .contains("A bachelor's degree appears to be required.")
                .anyMatch(caution -> caution.contains("not available"));
    }

    private DiscoveredJob job(
            String title,
            String description,
            Integer minimumExperience,
            Integer maximumExperience) {
        return new DiscoveredJob(
                "job-1",
                JobProvider.GREENHOUSE,
                "Example",
                title,
                "Remote, US",
                "US",
                WorkplaceType.REMOTE,
                description,
                "https://example.com/job-1",
                Instant.now(),
                null,
                minimumExperience,
                maximumExperience,
                true);
    }
}
