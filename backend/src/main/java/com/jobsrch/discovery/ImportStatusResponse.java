package com.jobsrch.discovery;

import java.time.Instant;
import java.util.List;

public record ImportStatusResponse(
        long activeJobs,
        Instant newestActiveJobSeenAt,
        List<ImportBatchResponse> recentBatches) {
}
