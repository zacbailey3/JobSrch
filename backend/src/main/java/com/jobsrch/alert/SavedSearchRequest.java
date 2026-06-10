package com.jobsrch.alert;

import com.jobsrch.discovery.WorkplaceType;
import com.jobsrch.discovery.CareerStage;
import com.jobsrch.discovery.DegreeRequirement;
import com.jobsrch.discovery.OpportunityType;
import com.jobsrch.discovery.SponsorshipStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SavedSearchRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 500) String query,
        @Size(max = 240) String location,
        @Size(max = 10) String countryCode,
        WorkplaceType workplaceType,
        @Min(1) @Max(60) Integer postedWithinDays,
        boolean entryLevelOnly,
        OpportunityType opportunityType,
        CareerStage careerStage,
        DegreeRequirement degreeRequirement,
        SponsorshipStatus sponsorshipStatus,
        @Min(0) @Max(10) Integer maximumExperience,
        boolean alertsEnabled) {

    public SavedSearchRequest(
            String name,
            String query,
            String location,
            String countryCode,
            WorkplaceType workplaceType,
            Integer postedWithinDays,
            boolean entryLevelOnly,
            boolean alertsEnabled) {
        this(
                name,
                query,
                location,
                countryCode,
                workplaceType,
                postedWithinDays,
                entryLevelOnly,
                null,
                null,
                null,
                null,
                null,
                alertsEnabled);
    }
}
