package com.jobsrch.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
