package com.jobsrch.auth;

import jakarta.validation.constraints.Pattern;

public record DeleteAccountRequest(
        @Pattern(regexp = "DELETE", message = "Type DELETE exactly to confirm") String confirmation) {
}
