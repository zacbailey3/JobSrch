CREATE TABLE indexed_jobs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    source_key VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(30) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    company VARCHAR(200) NOT NULL,
    title VARCHAR(240) NOT NULL,
    location VARCHAR(240),
    country_code VARCHAR(10),
    workplace_type VARCHAR(30) NOT NULL,
    description TEXT,
    source_url VARCHAR(1200) NOT NULL,
    published_at TIMESTAMP(6),
    expires_at TIMESTAMP(6),
    experience_min INT,
    experience_max INT,
    entry_level_likely BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_indexed_job_active_country ON indexed_jobs (active, country_code);
CREATE INDEX idx_indexed_job_company ON indexed_jobs (company);
CREATE INDEX idx_indexed_job_published ON indexed_jobs (published_at);

CREATE TABLE saved_searches (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    name VARCHAR(160) NOT NULL,
    query_text VARCHAR(500),
    location VARCHAR(240),
    country_code VARCHAR(10),
    workplace_type VARCHAR(30),
    posted_within_days INT,
    entry_level_only BOOLEAN NOT NULL,
    alerts_enabled BOOLEAN NOT NULL,
    last_checked_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_saved_search_user FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_saved_search_user ON saved_searches (user_id, created_at);

CREATE TABLE search_alert_matches (
    id BINARY(16) NOT NULL PRIMARY KEY,
    saved_search_id BINARY(16) NOT NULL,
    indexed_job_id BINARY(16) NOT NULL,
    discovered_at TIMESTAMP(6) NOT NULL,
    seen BOOLEAN NOT NULL,
    CONSTRAINT fk_alert_search FOREIGN KEY (saved_search_id) REFERENCES saved_searches (id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_job FOREIGN KEY (indexed_job_id) REFERENCES indexed_jobs (id) ON DELETE CASCADE,
    CONSTRAINT uq_alert_search_job UNIQUE (saved_search_id, indexed_job_id)
);

CREATE INDEX idx_alert_search_seen ON search_alert_matches (saved_search_id, seen);
