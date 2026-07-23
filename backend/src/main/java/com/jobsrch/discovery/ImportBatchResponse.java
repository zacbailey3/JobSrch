package com.jobsrch.discovery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportBatchResponse(
        UUID id,
        ImportBatchStatus status,
        Instant startedAt,
        Instant completedAt,
        int jobsReceived,
        int jobsExpired,
        int failureCount,
        String errorMessage,
        List<ImportAttemptResponse> attempts) {
}
