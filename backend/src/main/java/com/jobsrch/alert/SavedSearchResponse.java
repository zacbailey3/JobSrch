package com.jobsrch.alert;

import java.time.Instant;
import java.util.UUID;

import com.jobsrch.discovery.WorkplaceType;

public record SavedSearchResponse(
        UUID id,
        String name,
        String query,
        String location,
        String countryCode,
        WorkplaceType workplaceType,
        Integer postedWithinDays,
        boolean entryLevelOnly,
        boolean alertsEnabled,
        Instant lastCheckedAt,
        Instant createdAt) {

    static SavedSearchResponse from(SavedSearch search) {
        return new SavedSearchResponse(
                search.getId(),
                search.getName(),
                search.getQuery(),
                search.getLocation(),
                search.getCountryCode(),
                search.getWorkplaceType(),
                search.getPostedWithinDays(),
                search.isEntryLevelOnly(),
                search.isAlertsEnabled(),
                search.getLastCheckedAt(),
                search.getCreatedAt());
    }
}
