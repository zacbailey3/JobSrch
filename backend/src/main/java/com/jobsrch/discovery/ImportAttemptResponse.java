package com.jobsrch.discovery;

import java.time.Instant;

public record ImportAttemptResponse(
        JobProvider provider,
        ImportSourceType sourceType,
        String sourceKey,
        String sourceName,
        ImportAttemptStatus status,
        Instant startedAt,
        Instant completedAt,
        int jobsReceived,
        String errorMessage) {

    static ImportAttemptResponse from(JobImportAttempt attempt) {
        return new ImportAttemptResponse(
                attempt.getProvider(),
                attempt.getSourceType(),
                attempt.getSourceKey(),
                attempt.getSourceName(),
                attempt.getStatus(),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                attempt.getJobsReceived(),
                attempt.getErrorMessage());
    }
}
