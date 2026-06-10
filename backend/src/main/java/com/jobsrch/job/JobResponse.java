package com.jobsrch.job;

import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String company,
        String title,
        String location,
        String description,
        String sourceUrl,
        Integer experienceMin,
        Integer experienceMax,
        Instant publishedAt,
        Instant createdAt) {

    static JobResponse from(JobPosting job) {
        return new JobResponse(
                job.getId(),
                job.getCompany(),
                job.getTitle(),
                job.getLocation(),
                job.getDescription(),
                job.getSourceUrl(),
                job.getExperienceMin(),
                job.getExperienceMax(),
                job.getPublishedAt(),
                job.getCreatedAt());
    }
}
