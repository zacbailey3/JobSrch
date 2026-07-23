package com.jobsrch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.auth-cookie")
public record AuthCookieProperties(
        String name,
        boolean secure,
        String sameSite) {
}
