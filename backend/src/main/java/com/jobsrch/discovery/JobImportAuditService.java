package com.jobsrch.discovery;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists operational import evidence in independent transactions. A later
 * indexing failure therefore cannot erase the provider attempts needed to
 * diagnose that failure.
 */
@Service
public class JobImportAuditService {

    private final JobImportBatchRepository batches;
    private final JobImportAttemptRepository attempts;
    private final long retentionDays;

    public JobImportAuditService(
            JobImportBatchRepository batches,
            JobImportAttemptRepository attempts,
            @Value("${jobsrch.import.audit-retention-days:30}") long retentionDays) {
        this.batches = batches;
        this.attempts = attempts;
        this.retentionDays = retentionDays;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID startBatch() {
        batches.deleteStartedBefore(Instant.now().minusSeconds(retentionDays * 86_400));
        UUID id = UUID.randomUUID();
        batches.save(new JobImportBatch(id, Instant.now()));
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(
            UUID batchId,
            JobProvider provider,
            ImportSourceType sourceType,
            String sourceKey,
            String sourceName,
            Instant startedAt,
            int jobsReceived) {
        attempts.save(new JobImportAttempt(
                batchId, provider, sourceType, sourceKey, sourceName,
                ImportAttemptStatus.SUCCESS, startedAt, jobsReceived, null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(
            UUID batchId,
            JobProvider provider,
            ImportSourceType sourceType,
            String sourceKey,
            String sourceName,
            Instant startedAt) {
        attempts.save(new JobImportAttempt(
                batchId, provider, sourceType, sourceKey, sourceName,
                ImportAttemptStatus.FAILED, startedAt, 0, "Provider request failed"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID batchId, int received, int expired, int failures) {
        JobImportBatch batch = batches.findById(batchId).orElseThrow();
        batch.complete(received, expired, failures);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID batchId, int received, int failures) {
        JobImportBatch batch = batches.findById(batchId).orElseThrow();
        batch.fail(received, failures, "Index update or alert processing failed");
    }
}
