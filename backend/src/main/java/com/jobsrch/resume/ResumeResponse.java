package com.jobsrch.resume;

import java.time.Instant;
import java.util.UUID;

public record ResumeResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        Instant uploadedAt) {

    static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getOriginalFilename(),
                resume.getContentType(),
                resume.getSizeBytes(),
                resume.getUploadedAt());
    }
}
