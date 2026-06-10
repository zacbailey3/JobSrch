package com.jobsrch.alert;

import java.time.Instant;
import java.util.UUID;

import com.jobsrch.discovery.DiscoveredJob;

public record SearchAlertResponse(
        UUID id,
        UUID savedSearchId,
        String savedSearchName,
        DiscoveredJob job,
        Instant discoveredAt,
        boolean seen) {

    static SearchAlertResponse from(SearchAlertMatch match) {
        return new SearchAlertResponse(
                match.getId(),
                match.getSavedSearchId(),
                match.getSavedSearchName(),
                match.getIndexedJob().toDiscoveredJob(),
                match.getDiscoveredAt(),
                match.isSeen());
    }
}
