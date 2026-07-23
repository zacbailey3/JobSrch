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
@Table(name = "job_import_attempts")
public class JobImportAttempt {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ImportSourceType sourceType;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportAttemptStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "jobs_received", nullable = false)
    private int jobsReceived;

    @Column(name = "error_message")
    private String errorMessage;

    protected JobImportAttempt() {
    }

    public JobImportAttempt(
            UUID batchId,
            JobProvider provider,
            ImportSourceType sourceType,
            String sourceKey,
            String sourceName,
            ImportAttemptStatus status,
            Instant startedAt,
            int jobsReceived,
            String errorMessage) {
        this.id = UUID.randomUUID();
        this.batchId = batchId;
        this.provider = provider;
        this.sourceType = sourceType;
        this.sourceKey = sourceKey;
        this.sourceName = sourceName;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = Instant.now();
        this.jobsReceived = jobsReceived;
        this.errorMessage = errorMessage;
    }

    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public JobProvider getProvider() { return provider; }
    public ImportSourceType getSourceType() { return sourceType; }
    public String getSourceKey() { return sourceKey; }
    public String getSourceName() { return sourceName; }
    public ImportAttemptStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getJobsReceived() { return jobsReceived; }
    public String getErrorMessage() { return errorMessage; }
}
