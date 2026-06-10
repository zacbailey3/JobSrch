package com.jobsrch.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    List<JobPosting> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<JobPosting> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);
}
