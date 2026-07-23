package com.jobsrch.discovery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobImportStatusService {

    private final JobImportBatchRepository batches;
    private final JobImportAttemptRepository attempts;
    private final IndexedJobRepository jobs;

    public JobImportStatusService(
            JobImportBatchRepository batches,
            JobImportAttemptRepository attempts,
            IndexedJobRepository jobs) {
        this.batches = batches;
        this.attempts = attempts;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public ImportStatusResponse status() {
        var recent = batches.findTop20ByOrderByStartedAtDesc().stream()
                .map(batch -> new ImportBatchResponse(
                        batch.getId(),
                        batch.getStatus(),
                        batch.getStartedAt(),
                        batch.getCompletedAt(),
                        batch.getJobsReceived(),
                        batch.getJobsExpired(),
                        batch.getFailureCount(),
                        batch.getErrorMessage(),
                        attempts.findByBatchIdOrderByStartedAtAsc(batch.getId()).stream()
                                .map(ImportAttemptResponse::from)
                                .toList()))
                .toList();
        return new ImportStatusResponse(
                jobs.countByActiveTrue(),
                jobs.findNewestActiveLastSeenAt(),
                recent);
    }
}
