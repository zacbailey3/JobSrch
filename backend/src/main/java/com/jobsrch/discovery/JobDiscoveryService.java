package com.jobsrch.discovery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.discovery.JobBoardCatalog.JobBoard;

@Service
public class JobDiscoveryService {

    private static final int RESULT_LIMIT = 100;
    private static final Set<String> CAREER_STAGE_TERMS = Set.of(
            "entry", "junior", "jr", "graduate", "grad", "new", "intern");

    private final Map<JobProvider, JobProviderClient> clients;
    private final JobBoardCatalog catalog;

    public JobDiscoveryService(
            List<JobProviderClient> clients,
            JobBoardCatalog catalog) {
        this.clients = new EnumMap<>(JobProvider.class);
        this.catalog = catalog;
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    /**
     * Fetches transient provider results, then applies broad candidate-focused
     * filters. With no company identifier, the starter board catalog supplies
     * sources; a supplied identifier preserves direct company-board lookup.
     */
    public List<DiscoveredJob> search(
            JobProvider provider,
            String companyIdentifier,
            String companyName,
            String query,
            String location,
            boolean entryLevelOnly) {
        boolean directBoardSearch = companyIdentifier != null && !companyIdentifier.isBlank();
        List<JobBoard> boards = directBoardSearch
                ? directBoards(provider, companyIdentifier, companyName)
                : catalog.list(provider);
        List<String> queryTerms = queryTerms(query);

        Map<String, DiscoveredJob> uniqueJobs = new LinkedHashMap<>();
        boards.forEach(board -> fetchBoard(board).forEach(job ->
                uniqueJobs.putIfAbsent(uniqueKey(job), job)));

        List<ScoredJob> matches = uniqueJobs.values().stream()
                .filter(job -> !entryLevelOnly || job.entryLevelLikely())
                .filter(job -> directBoardSearch || isBlank(companyName)
                        || contains(job.company(), normalize(companyName)))
                .filter(job -> isBlank(location)
                        || contains(job.location(), normalize(location)))
                .map(job -> new ScoredJob(job, relevance(job, query, queryTerms)))
                .filter(scored -> queryTerms.isEmpty() || scored.relevance() > 0)
                .sorted(Comparator.comparingInt(ScoredJob::relevance).reversed())
                .limit(RESULT_LIMIT)
                .toList();

        return matches.stream()
                .map(ScoredJob::job)
                .toList();
    }

    private List<JobBoard> directBoards(
            JobProvider provider,
            String companyIdentifier,
            String companyName) {
        String identifier = ProviderSupport.validateIdentifier(companyIdentifier.trim());
        String displayName = isBlank(companyName) ? identifier : companyName.trim();
        List<JobBoard> boards = new ArrayList<>();
        if (provider == null) {
            for (JobProvider candidate : JobProvider.values()) {
                boards.add(new JobBoard(candidate, identifier, displayName));
            }
        } else {
            boards.add(new JobBoard(provider, identifier, displayName));
        }
        return boards;
    }

    /**
     * One unavailable public board should not make a multi-company search fail.
     * Direct provider clients still enforce their own timeout and host rules.
     */
    private List<DiscoveredJob> fetchBoard(JobBoard board) {
        JobProviderClient client = clients.get(board.provider());
        if (client == null) {
            return List.of();
        }
        try {
            return client.fetch(board.identifier(), board.companyName());
        } catch (ResponseStatusException ignored) {
            return List.of();
        }
    }

    private List<String> queryTerms(String query) {
        if (isBlank(query)) {
            return List.of();
        }
        return List.of(normalize(query).split("[^a-z0-9+#.]+")).stream()
                .filter(term -> term.length() > 1)
                .filter(term -> !CAREER_STAGE_TERMS.contains(term))
                .distinct()
                .toList();
    }

    private int relevance(DiscoveredJob job, String query, List<String> terms) {
        if (terms.isEmpty()) {
            return 0;
        }
        String title = normalize(job.title());
        String company = normalize(job.company());
        String location = normalize(job.location());
        String description = normalize(job.description());
        String phrase = normalize(query);
        int score = title.contains(phrase) ? 30 : 0;
        for (String term : terms) {
            if (title.contains(term)) {
                score += 12;
            }
            if (company.contains(term)) {
                score += 6;
            }
            if (location.contains(term)) {
                score += 4;
            }
            if (description.contains(term)) {
                score += 1;
            }
        }
        return score;
    }

    private String uniqueKey(DiscoveredJob job) {
        return !isBlank(job.sourceUrl())
                ? job.sourceUrl()
                : job.provider() + ":" + job.externalId();
    }

    private boolean contains(String value, String query) {
        return normalize(value).contains(query);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ScoredJob(DiscoveredJob job, int relevance) {
    }
}
