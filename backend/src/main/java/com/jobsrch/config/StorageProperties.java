package com.jobsrch.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem locations used for user-owned uploads.
 *
 * <p>The directory is configurable so local development, tests, and a deployed
 * environment can use different storage without changing application code.</p>
 */
@ConfigurationProperties(prefix = "jobsrch.storage")
public record StorageProperties(Path resumeDirectory) {
}
