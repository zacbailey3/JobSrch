package com.jobsrch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.providers.adzuna")
public record AdzunaProperties(String appId, String appKey) {

    public boolean enabled() {
        return appId != null && !appId.isBlank()
                && appKey != null && !appKey.isBlank();
    }
}
