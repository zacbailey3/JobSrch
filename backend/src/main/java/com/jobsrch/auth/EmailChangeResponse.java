package com.jobsrch.auth;

import java.time.Instant;

public record EmailChangeResponse(
        String message,
        String developmentToken,
        Instant expiresAt) {
}
