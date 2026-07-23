package com.jobsrch.discovery;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobImportAttemptRepository extends JpaRepository<JobImportAttempt, UUID> {

    List<JobImportAttempt> findByBatchIdOrderByStartedAtAsc(UUID batchId);
}
