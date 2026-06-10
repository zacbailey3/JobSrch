package com.jobsrch.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.jwt")
public record JwtProperties(String issuer, String secret, Duration ttl) {
}
