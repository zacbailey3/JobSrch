package com.jobsrch.job;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobRequest(
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String location,
        String description,
        @Size(max = 1000) String sourceUrl,
        @Min(0) @Max(50) Integer experienceMin,
        @Min(0) @Max(50) Integer experienceMax,
        Instant publishedAt) {

    @AssertTrue(message = "maximum experience must be greater than or equal to minimum experience")
    public boolean isExperienceRangeValid() {
        return experienceMin == null || experienceMax == null || experienceMax >= experienceMin;
    }
}
