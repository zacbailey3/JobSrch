package com.jobsrch.alert;

import com.jobsrch.discovery.WorkplaceType;

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
        boolean alertsEnabled) {
}
