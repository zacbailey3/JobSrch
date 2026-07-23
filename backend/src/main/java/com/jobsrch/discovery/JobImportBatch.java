package com.jobsrch.discovery;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_import_batches")
public class JobImportBatch {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportBatchStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "jobs_received", nullable = false)
    private int jobsReceived;

    @Column(name = "jobs_expired", nullable = false)
    private int jobsExpired;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "error_message")
    private String errorMessage;

    protected JobImportBatch() {
    }

    public JobImportBatch(UUID id, Instant startedAt) {
        this.id = id;
        this.status = ImportBatchStatus.RUNNING;
        this.startedAt = startedAt;
    }

    public void complete(int jobsReceived, int jobsExpired, int failureCount) {
        this.status = failureCount == 0
                ? ImportBatchStatus.SUCCESS
                : ImportBatchStatus.PARTIAL_FAILURE;
        this.jobsReceived = jobsReceived;
        this.jobsExpired = jobsExpired;
        this.failureCount = failureCount;
        this.completedAt = Instant.now();
    }

    public void fail(int jobsReceived, int failureCount, String errorMessage) {
        this.status = ImportBatchStatus.FAILED;
        this.jobsReceived = jobsReceived;
        this.failureCount = failureCount;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ImportBatchStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getJobsReceived() { return jobsReceived; }
    public int getJobsExpired() { return jobsExpired; }
    public int getFailureCount() { return failureCount; }
    public String getErrorMessage() { return errorMessage; }
}
