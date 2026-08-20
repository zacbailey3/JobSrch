package com.jobsrch.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordReauthenticationRequest(@NotBlank String password) {
}
