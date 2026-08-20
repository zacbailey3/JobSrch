package com.jobsrch.auth;

import java.time.Instant;

import com.jobsrch.user.UserAccount;

public record AccountResponse(
        String email,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant authenticatedAt,
        Instant recentAuthenticationExpiresAt) {

    static AccountResponse from(
            UserAccount user,
            Instant authenticatedAt,
            Instant recentAuthenticationExpiresAt) {
        return new AccountResponse(
                user.getEmail(),
                user.getEmailVerifiedAt() != null,
                user.getEmailVerifiedAt(),
                authenticatedAt,
                recentAuthenticationExpiresAt);
    }
}
