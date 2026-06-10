package com.jobsrch.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByUserIdOrderByUploadedAtDesc(UUID userId);

    Optional<Resume> findByIdAndUserId(UUID id, UUID userId);
}
