package com.jobsrch.auth;

import java.time.Instant;

public record PasswordResetResponse(
        String message,
        String developmentResetToken,
        Instant expiresAt) {
}
