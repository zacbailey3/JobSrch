package com.jobsrch.alert;

import java.time.Instant;
import java.util.UUID;

import com.jobsrch.discovery.WorkplaceType;
import com.jobsrch.discovery.CareerStage;
import com.jobsrch.discovery.DegreeRequirement;
import com.jobsrch.discovery.OpportunityType;
import com.jobsrch.discovery.SponsorshipStatus;

public record SavedSearchResponse(
        UUID id,
        String name,
        String query,
        String location,
        String countryCode,
        WorkplaceType workplaceType,
        Integer postedWithinDays,
        boolean entryLevelOnly,
        OpportunityType opportunityType,
        CareerStage careerStage,
        DegreeRequirement degreeRequirement,
        SponsorshipStatus sponsorshipStatus,
        Integer maximumExperience,
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
                search.getOpportunityType(),
                search.getCareerStage(),
                search.getDegreeRequirement(),
                search.getSponsorshipStatus(),
                search.getMaximumExperience(),
                search.isAlertsEnabled(),
                search.getLastCheckedAt(),
                search.getCreatedAt());
    }
}
