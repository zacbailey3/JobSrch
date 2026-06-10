package com.jobsrch.discovery;

import java.time.Instant;

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
        boolean entryLevelLikely) {
}
