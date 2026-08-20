package com.jobsrch.auth;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_change_tokens")
class EmailChangeToken {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID userId;

    @Column(name = "new_email", nullable = false)
    private String newEmail;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailChangeToken() {
    }

    EmailChangeToken(UUID userId, String newEmail, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.newEmail = newEmail;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    UUID getUserId() {
        return userId;
    }

    String getNewEmail() {
        return newEmail;
    }

    boolean canBeUsedAt(Instant instant) {
        return usedAt == null && expiresAt.isAfter(instant);
    }

    void markUsed(Instant instant) {
        usedAt = instant;
    }
}
