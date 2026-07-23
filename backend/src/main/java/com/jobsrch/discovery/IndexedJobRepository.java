package com.jobsrch.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IndexedJobRepository extends JpaRepository<IndexedJob, UUID> {

    Optional<IndexedJob> findBySourceKey(String sourceKey);

    List<IndexedJob> findByActiveTrue();

    List<IndexedJob> findByActiveTrueAndLastSeenAtBefore(Instant cutoff);

    List<IndexedJob> findByActiveTrueAndExpiresAtBefore(Instant cutoff);

    long countByActiveTrue();

    @Query("select max(job.lastSeenAt) from IndexedJob job where job.active = true")
    Instant findNewestActiveLastSeenAt();
}
