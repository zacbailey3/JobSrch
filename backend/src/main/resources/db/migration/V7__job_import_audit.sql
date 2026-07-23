CREATE TABLE job_import_batches (
    id BINARY(16) NOT NULL PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    jobs_received INT NOT NULL,
    jobs_expired INT NOT NULL,
    failure_count INT NOT NULL,
    error_message VARCHAR(240)
);

CREATE INDEX idx_import_batch_started ON job_import_batches (started_at);

CREATE TABLE job_import_attempts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    batch_id BINARY(16) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    source_key VARCHAR(240) NOT NULL,
    source_name VARCHAR(240) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL,
    jobs_received INT NOT NULL,
    error_message VARCHAR(240),
    CONSTRAINT fk_import_attempt_batch
        FOREIGN KEY (batch_id) REFERENCES job_import_batches (id) ON DELETE CASCADE
);

CREATE INDEX idx_import_attempt_batch ON job_import_attempts (batch_id);
CREATE INDEX idx_import_attempt_provider_completed
    ON job_import_attempts (provider, completed_at);
