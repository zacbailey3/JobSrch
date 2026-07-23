package com.jobsrch.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.email")
public record EmailProperties(String resendApiKey, String from, URI frontendBaseUrl) {

    public boolean isConfigured() {
        return resendApiKey != null && !resendApiKey.isBlank()
                && from != null && !from.isBlank()
                && frontendBaseUrl != null;
    }
}
