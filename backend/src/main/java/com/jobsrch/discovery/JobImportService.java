package com.jobsrch.discovery;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.jobsrch.alert.SavedSearchAlertService;

/**
 * Periodically refreshes the shared job index and expires postings no provider
 * has returned recently.
 */
@Service
public class JobImportService {

    private static final List<String> AGGREGATE_QUERIES = List.of(
            "software engineer",
            "software engineer I",
            "software developer",
            "data analyst",
            "quality assurance",
            "IT support");

    private final JobBoardCatalog catalog;
    private final Map<JobProvider, JobProviderClient> boardClients;
    private final List<AggregateJobProviderClient> aggregateClients;
    private final JobIndexService index;
    private final SavedSearchAlertService alerts;
    private final JobImportAuditService audit;
    private final boolean enabled;
    private final Duration expireAfter;

    public JobImportService(
            JobBoardCatalog catalog,
            List<JobProviderClient> boardClients,
            List<AggregateJobProviderClient> aggregateClients,
            JobIndexService index,
            SavedSearchAlertService alerts,
            JobImportAuditService audit,
            @Value("${jobsrch.import.enabled:true}") boolean enabled,
            @Value("${jobsrch.import.expire-after-hours:72}") long expireAfterHours) {
        this.catalog = catalog;
        this.boardClients = boardClients.stream()
                .collect(Collectors.toMap(JobProviderClient::provider, Function.identity()));
        this.aggregateClients = aggregateClients;
        this.index = index;
        this.alerts = alerts;
        this.audit = audit;
        this.enabled = enabled;
        this.expireAfter = Duration.ofHours(expireAfterHours);
    }

    @Scheduled(
            initialDelayString = "${jobsrch.import.initial-delay-ms:15000}",
            fixedDelayString = "${jobsrch.import.fixed-delay-ms:21600000}")
    public void refresh() {
        if (!enabled) {
            return;
        }
        UUID batchId = audit.startBatch();
        List<DiscoveredJob> imported = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger();
        catalog.list(null).parallelStream().forEach(board -> {
            JobProviderClient client = boardClients.get(board.provider());
            if (client == null) {
                return;
            }
            Instant startedAt = Instant.now();
            try {
                List<DiscoveredJob> jobs = client.fetch(board.identifier(), board.companyName());
                imported.addAll(jobs);
                audit.success(
                        batchId, board.provider(), ImportSourceType.COMPANY_BOARD,
                        board.identifier(), board.companyName(), startedAt, jobs.size());
            } catch (RuntimeException exception) {
                // One unavailable company board must not abort the full refresh.
                failures.incrementAndGet();
                audit.failure(
                        batchId, board.provider(), ImportSourceType.COMPANY_BOARD,
                        board.identifier(), board.companyName(), startedAt);
            }
        });
        for (AggregateJobProviderClient client : aggregateClients) {
            if (!client.enabled()) {
                continue;
            }
            for (String query : AGGREGATE_QUERIES) {
                Instant startedAt = Instant.now();
                try {
                    List<DiscoveredJob> jobs = client.search(query, null, 30);
                    imported.addAll(jobs);
                    audit.success(
                            batchId, client.provider(), ImportSourceType.AGGREGATE_QUERY,
                            query, query, startedAt, jobs.size());
                } catch (RuntimeException exception) {
                    failures.incrementAndGet();
                    audit.failure(
                            batchId, client.provider(), ImportSourceType.AGGREGATE_QUERY,
                            query, query, startedAt);
                }
            }
        }
        try {
            index.upsertAll(List.copyOf(imported));
            int expired = index.expireStale(expireAfter);
            alerts.refreshAll();
            audit.complete(batchId, imported.size(), expired, failures.get());
        } catch (RuntimeException exception) {
            audit.fail(batchId, imported.size(), failures.get());
            throw exception;
        }
    }
}
