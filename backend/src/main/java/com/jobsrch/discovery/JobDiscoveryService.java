package com.jobsrch.discovery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.jwt.Jwt;

import com.jobsrch.profile.ProfileResponse;
import com.jobsrch.profile.ProfileService;

@Service
public class JobDiscoveryService {

    private static final int RESULT_LIMIT = 100;
    private static final int COMPANY_RESULT_LIMIT = 5;
    private static final Set<String> CAREER_STAGE_TERMS = Set.of(
            "entry", "junior", "jr", "graduate", "grad", "new", "intern");

    private final Map<JobProvider, JobProviderClient> clients;
    private final JobIndexService index;
    private final JobInsightClassifier insights;
    private final CandidateMatchExplainer matchExplainer;
    private final ProfileService profiles;

    public JobDiscoveryService(
            List<JobProviderClient> clients,
            JobIndexService index,
            JobInsightClassifier insights,
            CandidateMatchExplainer matchExplainer,
            ProfileService profiles) {
        this.clients = new EnumMap<>(JobProvider.class);
        this.index = index;
        this.insights = insights;
        this.matchExplainer = matchExplainer;
        this.profiles = profiles;
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    /**
     * Searches the shared local index for normal role searches. A supplied
     * company identifier preserves direct live board lookup for targeted checks.
     */
    public List<DiscoveredJob> search(
            Jwt jwt,
            JobProvider provider,
            String companyIdentifier,
            String companyName,
            String query,
            String location,
            String countryCode,
            WorkplaceType workplaceType,
            Integer postedWithinDays,
            DiscoverySort sort,
            boolean entryLevelOnly,
            OpportunityType opportunityType,
            CareerStage careerStage,
            DegreeRequirement degreeRequirement,
            SponsorshipStatus sponsorshipStatus,
            Integer maximumExperience) {
        ProfileResponse profile = jwt == null ? null : profiles.get(jwt);
        return search(
                profile,
                provider,
                companyIdentifier,
                companyName,
                query,
                location,
                countryCode,
                workplaceType,
                postedWithinDays,
                sort,
                entryLevelOnly,
                opportunityType,
                careerStage,
                degreeRequirement,
                sponsorshipStatus,
                maximumExperience);
    }

    List<DiscoveredJob> search(
            ProfileResponse profile,
            JobProvider provider,
            String companyIdentifier,
            String companyName,
            String query,
            String location,
            String countryCode,
            WorkplaceType workplaceType,
            Integer postedWithinDays,
            DiscoverySort sort,
            boolean entryLevelOnly,
            OpportunityType opportunityType,
            CareerStage careerStage,
            DegreeRequirement degreeRequirement,
            SponsorshipStatus sponsorshipStatus,
            Integer maximumExperience) {
        validatePostedWithinDays(postedWithinDays);
        validateMaximumExperience(maximumExperience);
        boolean directBoardSearch = companyIdentifier != null && !companyIdentifier.isBlank();
        List<String> queryTerms = queryTerms(query);

        Map<String, DiscoveredJob> uniqueJobs = new LinkedHashMap<>();
        List<IndexedJob> indexedJobs = index.activeJobs();
        indexedJobs.stream()
                .map(IndexedJob::toDiscoveredJob)
                .filter(job -> provider == null || job.provider() == provider)
                .forEach(job -> uniqueJobs.putIfAbsent(uniqueKey(job), job));

        if (directBoardSearch) {
            List<DirectBoard> boards = directBoards(provider, companyIdentifier, companyName);
            List<DiscoveredJob> liveJobs = new ArrayList<>();
            boards.parallelStream().forEach(board -> {
                List<DiscoveredJob> fetched = fetchBoard(board);
                synchronized (liveJobs) {
                    liveJobs.addAll(fetched);
                }
            });
            index.upsertAll(liveJobs);
            liveJobs.forEach(job -> uniqueJobs.putIfAbsent(uniqueKey(job), job));
        }

        List<ScoredJob> matches = uniqueJobs.values().stream()
                .map(insights::enrich)
                .filter(job -> !entryLevelOnly || job.entryLevelLikely())
                .filter(job -> opportunityType == null || job.opportunityType() == opportunityType)
                .filter(job -> careerStage == null || job.careerStage() == careerStage)
                .filter(job -> degreeRequirement == null
                        || job.degreeRequirement() == degreeRequirement)
                .filter(job -> sponsorshipStatus == null
                        || job.sponsorshipStatus() == sponsorshipStatus)
                .filter(job -> maximumExperience == null
                        || job.experienceMax() == null
                        || job.experienceMax() <= maximumExperience)
                .filter(job -> directBoardSearch || isBlank(companyName)
                        || contains(job.company(), normalize(companyName)))
                .filter(job -> isBlank(location)
                        || contains(job.location(), normalize(location)))
                .filter(job -> matchesCountry(job, countryCode))
                .filter(job -> workplaceType == null
                        || job.workplaceType() == workplaceType)
                .filter(job -> postedWithinDays == null
                        || isRecent(job.publishedAt(), postedWithinDays))
                .map(job -> new ScoredJob(job, relevance(job, query, queryTerms)))
                .filter(scored -> queryTerms.isEmpty() || scored.relevance() > 0)
                .sorted(resultComparator(sort))
                .toList();

        return balanceCompanies(matches).stream()
                .map(ScoredJob::job)
                .map(job -> matchExplainer.explain(job, profile))
                .toList();
    }

    List<DiscoveredJob> search(
            JobProvider provider,
            String companyIdentifier,
            String companyName,
            String query,
            String location,
            String countryCode,
            WorkplaceType workplaceType,
            Integer postedWithinDays,
            DiscoverySort sort,
            boolean entryLevelOnly) {
        return search(
                (ProfileResponse) null,
                provider,
                companyIdentifier,
                companyName,
                query,
                location,
                countryCode,
                workplaceType,
                postedWithinDays,
                sort,
                entryLevelOnly,
                null,
                null,
                null,
                null,
                null);
    }

    private void validatePostedWithinDays(Integer postedWithinDays) {
        if (postedWithinDays != null
                && (postedWithinDays < 1 || postedWithinDays > 60)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Posted-within days must be between 1 and 60");
        }
    }

    private void validateMaximumExperience(Integer maximumExperience) {
        if (maximumExperience != null
                && (maximumExperience < 0 || maximumExperience > 10)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum experience must be between 0 and 10 years");
        }
    }

    private boolean isRecent(Instant publishedAt, int postedWithinDays) {
        return publishedAt != null
                && !publishedAt.isBefore(Instant.now().minus(postedWithinDays, ChronoUnit.DAYS));
    }

    private Comparator<ScoredJob> resultComparator(DiscoverySort sort) {
        Comparator<ScoredJob> byRelevance =
                Comparator.comparingInt(ScoredJob::relevance).reversed();
        Comparator<ScoredJob> byNewest = Comparator.comparing(
                scored -> scored.job().publishedAt(),
                Comparator.nullsLast(Comparator.reverseOrder()));
        return sort == DiscoverySort.NEWEST
                ? byNewest.thenComparing(byRelevance)
                : byRelevance.thenComparing(byNewest);
    }

    private List<DirectBoard> directBoards(
            JobProvider provider,
            String companyIdentifier,
            String companyName) {
        String identifier = ProviderSupport.validateIdentifier(companyIdentifier.trim());
        String displayName = isBlank(companyName) ? identifier : companyName.trim();
        List<DirectBoard> boards = new ArrayList<>();
        if (provider == null) {
            boards.add(new DirectBoard(JobProvider.GREENHOUSE, identifier, displayName));
            boards.add(new DirectBoard(JobProvider.LEVER, identifier, displayName));
        } else if (clients.containsKey(provider)) {
            boards.add(new DirectBoard(provider, identifier, displayName));
        }
        return boards;
    }

    /**
     * One unavailable public board should not make a multi-company search fail.
     * Direct provider clients still enforce their own timeout and host rules.
     */
    private List<DiscoveredJob> fetchBoard(DirectBoard board) {
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

    private List<ScoredJob> balanceCompanies(List<ScoredJob> sortedJobs) {
        Map<String, Integer> companyCounts = new LinkedHashMap<>();
        List<ScoredJob> balanced = new ArrayList<>();
        for (ScoredJob job : sortedJobs) {
            String company = normalize(job.job().company());
            int count = companyCounts.getOrDefault(company, 0);
            if (count >= COMPANY_RESULT_LIMIT) {
                continue;
            }
            companyCounts.put(company, count + 1);
            balanced.add(job);
            if (balanced.size() == RESULT_LIMIT) {
                break;
            }
        }
        return balanced;
    }

    private boolean contains(String value, String query) {
        return normalize(value).contains(query);
    }

    private boolean matchesCountry(DiscoveredJob job, String requestedCountryCode) {
        if (isBlank(requestedCountryCode)
                || "ANY".equalsIgnoreCase(requestedCountryCode)) {
            return true;
        }

        Set<String> locationCountries =
                ProviderSupport.inferCountryCodes(job.location());
        if (!locationCountries.isEmpty()) {
            return locationCountries.size() == 1
                    && locationCountries.contains(
                            requestedCountryCode.toUpperCase(Locale.ROOT));
        }
        return normalize(requestedCountryCode)
                .equals(normalize(job.countryCode()));
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

    private record DirectBoard(
            JobProvider provider,
            String identifier,
            String companyName) {
    }
}
