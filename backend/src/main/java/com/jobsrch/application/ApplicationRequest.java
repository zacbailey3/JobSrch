package com.jobsrch.application;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicationRequest(
        UUID jobPostingId,
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String sourceUrl,
        @NotNull ApplicationStatus status,
        LocalDate appliedAt,
        String notes) {
}
