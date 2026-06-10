package com.jobsrch.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            "software developer",
            "data analyst",
            "quality assurance",
            "IT support");

    private final JobBoardCatalog catalog;
    private final Map<JobProvider, JobProviderClient> boardClients;
    private final List<AggregateJobProviderClient> aggregateClients;
    private final JobIndexService index;
    private final SavedSearchAlertService alerts;
    private final boolean enabled;
    private final Duration expireAfter;

    public JobImportService(
            JobBoardCatalog catalog,
            List<JobProviderClient> boardClients,
            List<AggregateJobProviderClient> aggregateClients,
            JobIndexService index,
            SavedSearchAlertService alerts,
            @Value("${jobsrch.import.enabled:true}") boolean enabled,
            @Value("${jobsrch.import.expire-after-hours:72}") long expireAfterHours) {
        this.catalog = catalog;
        this.boardClients = boardClients.stream()
                .collect(Collectors.toMap(JobProviderClient::provider, Function.identity()));
        this.aggregateClients = aggregateClients;
        this.index = index;
        this.alerts = alerts;
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
        List<DiscoveredJob> imported = new ArrayList<>();
        catalog.list(null).parallelStream().forEach(board -> {
            JobProviderClient client = boardClients.get(board.provider());
            if (client == null) {
                return;
            }
            try {
                List<DiscoveredJob> jobs = client.fetch(board.identifier(), board.companyName());
                synchronized (imported) {
                    imported.addAll(jobs);
                }
            } catch (RuntimeException ignored) {
                // One unavailable company board must not abort the full refresh.
            }
        });
        for (AggregateJobProviderClient client : aggregateClients) {
            if (!client.enabled()) {
                continue;
            }
            for (String query : AGGREGATE_QUERIES) {
                imported.addAll(client.search(query, null, 30));
            }
        }
        index.upsertAll(imported);
        index.expireStale(expireAfter);
        alerts.refreshAll();
    }
}
