package com.jobsrch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.providers.usajobs")
public record UsaJobsProperties(String email, String apiKey) {

    public boolean enabled() {
        return email != null && !email.isBlank()
                && apiKey != null && !apiKey.isBlank();
    }
}
