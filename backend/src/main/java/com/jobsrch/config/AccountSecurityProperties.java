package com.jobsrch.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.account-security")
public record AccountSecurityProperties(
        Duration recentAuthenticationWindow,
        Duration emailChangeTtl,
        boolean exposeDevelopmentEmailChangeToken) {
}
