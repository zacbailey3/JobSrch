package com.jobsrch.discovery;

import java.time.Instant;
import java.util.List;

public record DiscoveredJob(
        String externalId,
        JobProvider provider,
        String company,
        String title,
        String location,
        String countryCode,
        WorkplaceType workplaceType,
        String description,
        String sourceUrl,
        Instant publishedAt,
        Instant expiresAt,
        Integer experienceMin,
        Integer experienceMax,
        boolean entryLevelLikely,
        OpportunityType opportunityType,
        CareerStage careerStage,
        DegreeRequirement degreeRequirement,
        SponsorshipStatus sponsorshipStatus,
        Instant verifiedAt,
        List<String> matchReasons,
        List<String> cautions) {

    public DiscoveredJob(
            String externalId,
            JobProvider provider,
            String company,
            String title,
            String location,
            String countryCode,
            WorkplaceType workplaceType,
            String description,
            String sourceUrl,
            Instant publishedAt,
            Instant expiresAt,
            Integer experienceMin,
            Integer experienceMax,
            boolean entryLevelLikely) {
        this(
                externalId,
                provider,
                company,
                title,
                location,
                countryCode,
                workplaceType,
                description,
                sourceUrl,
                publishedAt,
                expiresAt,
                experienceMin,
                experienceMax,
                entryLevelLikely,
                OpportunityType.UNKNOWN,
                CareerStage.UNSPECIFIED,
                DegreeRequirement.NOT_STATED,
                SponsorshipStatus.NOT_STATED,
                null,
                List.of(),
                List.of());
    }

    public DiscoveredJob withGuidance(List<String> reasons, List<String> warnings) {
        return new DiscoveredJob(
                externalId,
                provider,
                company,
                title,
                location,
                countryCode,
                workplaceType,
                description,
                sourceUrl,
                publishedAt,
                expiresAt,
                experienceMin,
                experienceMax,
                entryLevelLikely,
                opportunityType,
                careerStage,
                degreeRequirement,
                sponsorshipStatus,
                verifiedAt,
                reasons,
                warnings);
    }
}
