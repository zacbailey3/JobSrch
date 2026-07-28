ALTER TABLE user_accounts
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
