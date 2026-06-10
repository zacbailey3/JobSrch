package com.jobsrch.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @Size(max = 50) String phone,
        @Size(max = 200) String location,
        @Size(max = 240) String headline,
        @Size(max = 240) String education,
        @Min(1950) @Max(2200) Integer graduationYear,
        @Min(0) @Max(50) Integer yearsExperience,
        @Size(max = 1000) String desiredRoles,
        @Size(max = 2000) String skills,
        @Size(max = 1000) String linkedinUrl,
        @Size(max = 1000) String portfolioUrl) {
}
