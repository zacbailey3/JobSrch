package com.jobsrch.discovery;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JobImportBatchRepository extends JpaRepository<JobImportBatch, UUID> {

    List<JobImportBatch> findTop20ByOrderByStartedAtDesc();

    @Modifying
    @Query("delete from JobImportBatch batch where batch.startedAt < :cutoff")
    void deleteStartedBefore(Instant cutoff);
}
