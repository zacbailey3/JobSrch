CREATE TABLE user_accounts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE job_postings (
    id BINARY(16) NOT NULL PRIMARY KEY,
    owner_id BINARY(16) NOT NULL,
    company VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    location VARCHAR(200),
    description TEXT,
    source_url VARCHAR(1000),
    experience_min INT,
    experience_max INT,
    published_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_job_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_job_owner_created ON job_postings (owner_id, created_at);
CREATE INDEX idx_job_company ON job_postings (company);

CREATE TABLE job_applications (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    job_posting_id BINARY(16),
    company VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    source_url VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    applied_at DATE,
    notes TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_application_user FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_application_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id)
);

CREATE INDEX idx_application_user_status ON job_applications (user_id, status);
