package com.jobsrch.auth;

import java.time.Instant;
import java.util.UUID;

import com.jobsrch.user.UserAccount;

/**
 * Safe browser-visible account data. The JWT is deliberately excluded because
 * it is delivered only in an HttpOnly cookie.
 */
public record SessionResponse(
        long expiresIn,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        Instant authenticatedAt) {

    static SessionResponse from(AuthResponse response) {
        return new SessionResponse(
                response.expiresIn(),
                response.userId(),
                response.email(),
                response.firstName(),
                response.lastName(),
                response.authenticatedAt());
    }

    static SessionResponse from(UserAccount user, long expiresIn, Instant authenticatedAt) {
        return new SessionResponse(
                expiresIn,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                authenticatedAt);
    }
}
