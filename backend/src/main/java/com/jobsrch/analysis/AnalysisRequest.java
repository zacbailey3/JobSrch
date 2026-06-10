package com.jobsrch.analysis;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AnalysisRequest(
        @NotNull UUID resumeId,
        @NotNull UUID jobPostingId) {
}
