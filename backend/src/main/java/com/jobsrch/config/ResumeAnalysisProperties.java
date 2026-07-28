package com.jobsrch.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsrch.resume-analysis")
public record ResumeAnalysisProperties(
        Duration timeout,
        int workerCount,
        int queueCapacity,
        int maxPdfPages,
        int maxTextLength) {
}
