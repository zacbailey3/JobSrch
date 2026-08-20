package com.jobsrch.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChangeRequest(@Email @NotBlank String email) {
}
