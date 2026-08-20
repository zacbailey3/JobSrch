package com.jobsrch.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String firstName, String lastName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = Instant.now();
        this.securityVersion = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateVerifiedEmail(String email, Instant verifiedAt) {
        this.email = email;
        this.emailVerifiedAt = verifiedAt;
    }

    public long getSecurityVersion() {
        return securityVersion;
    }

    /**
     * Invalidates every JWT issued before a sensitive account change.
     */
    public void invalidateSessions() {
        securityVersion++;
    }
}
