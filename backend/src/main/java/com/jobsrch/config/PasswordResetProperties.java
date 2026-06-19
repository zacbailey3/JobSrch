package com.jobsrch.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.password-reset")
public record PasswordResetProperties(Duration ttl, boolean exposeDevelopmentToken) {
}
