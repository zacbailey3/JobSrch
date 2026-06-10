package com.jobsrch.auth;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        long expiresIn,
        UUID userId,
        String email,
        String firstName,
        String lastName) {
}
