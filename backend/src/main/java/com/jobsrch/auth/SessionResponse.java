package com.jobsrch.auth;

import java.util.UUID;

/**
 * Safe browser-visible account data. The JWT is deliberately excluded because
 * it is delivered only in an HttpOnly cookie.
 */
public record SessionResponse(
        long expiresIn,
        UUID userId,
        String email,
        String firstName,
        String lastName) {

    static SessionResponse from(AuthResponse response) {
        return new SessionResponse(
                response.expiresIn(),
                response.userId(),
                response.email(),
                response.firstName(),
                response.lastName());
    }
}
