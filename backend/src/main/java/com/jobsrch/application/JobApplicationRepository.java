package com.jobsrch.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, ApplicationStatus status);
}
