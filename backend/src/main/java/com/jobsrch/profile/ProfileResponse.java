package com.jobsrch.profile;

import java.time.Instant;

public record ProfileResponse(
        String phone,
        String location,
        String headline,
        String education,
        Integer graduationYear,
        Integer yearsExperience,
        String desiredRoles,
        String skills,
        String linkedinUrl,
        String portfolioUrl,
        Instant updatedAt) {

    static ProfileResponse empty() {
        return new ProfileResponse(null, null, null, null, null, null, null, null, null, null, null);
    }

    static ProfileResponse from(CareerProfile profile) {
        return new ProfileResponse(
                profile.getPhone(),
                profile.getLocation(),
                profile.getHeadline(),
                profile.getEducation(),
                profile.getGraduationYear(),
                profile.getYearsExperience(),
                profile.getDesiredRoles(),
                profile.getSkills(),
                profile.getLinkedinUrl(),
                profile.getPortfolioUrl(),
                profile.getUpdatedAt());
    }
}
