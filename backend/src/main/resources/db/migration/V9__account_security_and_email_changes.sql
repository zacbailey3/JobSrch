ALTER TABLE user_accounts
    ADD COLUMN email_verified_at TIMESTAMP(6) NULL;

CREATE TABLE email_change_tokens (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    new_email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_email_change_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_email_change_user ON email_change_tokens (user_id);
CREATE INDEX idx_email_change_expiry ON email_change_tokens (expires_at);
