CREATE TABLE career_profiles (
    user_id BINARY(16) NOT NULL PRIMARY KEY,
    phone VARCHAR(50),
    location VARCHAR(200),
    headline VARCHAR(240),
    education VARCHAR(240),
    graduation_year INT,
    years_experience INT,
    desired_roles VARCHAR(1000),
    skills VARCHAR(2000),
    linkedin_url VARCHAR(1000),
    portfolio_url VARCHAR(1000),
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);

CREATE TABLE resumes (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_resume_user_uploaded ON resumes (user_id, uploaded_at);
