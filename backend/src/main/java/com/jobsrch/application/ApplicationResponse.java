package com.jobsrch.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobPostingId,
        String company,
        String title,
        String sourceUrl,
        ApplicationStatus status,
        LocalDate appliedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    static ApplicationResponse from(JobApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getJobPostingId(),
                application.getCompany(),
                application.getTitle(),
                application.getSourceUrl(),
                application.getStatus(),
                application.getAppliedAt(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
