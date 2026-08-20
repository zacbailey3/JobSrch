package com.jobsrch.auth;

import jakarta.validation.constraints.NotBlank;

public record EmailChangeConfirmRequest(@NotBlank String token) {
}
