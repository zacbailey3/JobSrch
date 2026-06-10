package com.jobsrch.discovery;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the shared provider-neutral job index.
 *
 * <p>Source URLs are the preferred deduplication key because aggregators can
 * expose the same employer posting under different provider IDs.</p>
 */
@Service
public class JobIndexService {

    private final IndexedJobRepository jobs;
    private final JobInsightClassifier insights;

    public JobIndexService(IndexedJobRepository jobs, JobInsightClassifier insights) {
        this.jobs = jobs;
        this.insights = insights;
    }

    @Transactional
    public void upsertAll(List<DiscoveredJob> discoveredJobs) {
        Instant seenAt = Instant.now();
        for (DiscoveredJob discovered : discoveredJobs) {
            DiscoveredJob job = insights.enrich(discovered);
            String sourceKey = sourceKey(job);
            IndexedJob indexed = jobs.findBySourceKey(sourceKey)
                    .orElseGet(() -> new IndexedJob(sourceKey, job, seenAt));
            indexed.update(job, seenAt);
            jobs.save(indexed);
        }
    }

    @Transactional(readOnly = true)
    public List<IndexedJob> activeJobs() {
        return jobs.findByActiveTrue();
    }

    @Transactional
    public int expireStale(Duration maxAge) {
        Instant now = Instant.now();
        Map<java.util.UUID, IndexedJob> expired = new LinkedHashMap<>();
        jobs.findByActiveTrueAndLastSeenAtBefore(now.minus(maxAge))
                .forEach(job -> expired.put(job.getId(), job));
        jobs.findByActiveTrueAndExpiresAtBefore(now)
                .forEach(job -> expired.put(job.getId(), job));
        expired.values().forEach(IndexedJob::expire);
        return expired.size();
    }

    private String sourceKey(DiscoveredJob job) {
        String identity;
        if (job.sourceUrl() != null && !job.sourceUrl().isBlank()) {
            identity = job.sourceUrl().trim().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        } else {
            identity = (job.provider() + ":" + job.externalId()).toLowerCase(Locale.ROOT);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
