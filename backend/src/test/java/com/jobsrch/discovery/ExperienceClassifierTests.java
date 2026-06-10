package com.jobsrch.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExperienceClassifierTests {

    private final ExperienceClassifier classifier = new ExperienceClassifier();

    @Test
    void recognizesEntryLevelRangesAndSeniorTitles() {
        ExperienceClassifier.ExperienceClassification entry = classifier.classify(
                "Associate Software Engineer",
                "Candidates should have 0-2 years of Java experience.");
        ExperienceClassifier.ExperienceClassification senior = classifier.classify(
                "Senior Software Engineer",
                "Requires 5+ years of experience.");
        ExperienceClassifier.ExperienceClassification abbreviatedSenior = classifier.classify(
                "Sr. Forward Deployed Engineer",
                "Join the customer engineering team.");

        assertThat(entry.minimumYears()).isZero();
        assertThat(entry.maximumYears()).isEqualTo(2);
        assertThat(entry.entryLevelLikely()).isTrue();
        assertThat(senior.minimumYears()).isEqualTo(5);
        assertThat(senior.entryLevelLikely()).isFalse();
        assertThat(abbreviatedSenior.entryLevelLikely()).isFalse();
    }

    @Test
    void keepsUnknownNonSeniorRolesVisible() {
        ExperienceClassifier.ExperienceClassification result = classifier.classify(
                "Software Engineer",
                "Join a collaborative product team.");

        assertThat(result.minimumYears()).isNull();
        assertThat(result.maximumYears()).isNull();
        assertThat(result.entryLevelLikely()).isTrue();
    }
}
