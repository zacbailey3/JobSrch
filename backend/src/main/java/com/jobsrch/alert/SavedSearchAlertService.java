package com.jobsrch.alert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsrch.common.NotFoundException;
import com.jobsrch.discovery.IndexedJob;
import com.jobsrch.discovery.IndexedJobRepository;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

/**
 * Owns saved discovery filters and creates inbox-style matches when a later
 * import adds a job that satisfies those filters.
 */
@Service
public class SavedSearchAlertService {

    private final SavedSearchRepository savedSearches;
    private final SearchAlertMatchRepository matches;
    private final IndexedJobRepository indexedJobs;
    private final CurrentUserService currentUsers;

    public SavedSearchAlertService(
            SavedSearchRepository savedSearches,
            SearchAlertMatchRepository matches,
            IndexedJobRepository indexedJobs,
            CurrentUserService currentUsers) {
        this.savedSearches = savedSearches;
        this.matches = matches;
        this.indexedJobs = indexedJobs;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> list(Jwt jwt) {
        UUID userId = currentUsers.requireUser(jwt).getId();
        return savedSearches.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(SavedSearchResponse::from)
                .toList();
    }

    @Transactional
    public SavedSearchResponse create(Jwt jwt, SavedSearchRequest request) {
        UserAccount user = currentUsers.requireUser(jwt);
        SavedSearch search = new SavedSearch(user, request);
        // Existing indexed jobs are the baseline; alerts represent later imports.
        search.checked(Instant.now());
        return SavedSearchResponse.from(savedSearches.save(search));
    }

    @Transactional
    public void delete(Jwt jwt, UUID id) {
        UUID userId = currentUsers.requireUser(jwt).getId();
        SavedSearch search = savedSearches.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new NotFoundException("Saved search not found"));
        savedSearches.delete(search);
    }

    @Transactional(readOnly = true)
    public List<SearchAlertResponse> listAlerts(Jwt jwt) {
        UUID userId = currentUsers.requireUser(jwt).getId();
        return matches.findBySavedSearch_User_IdOrderByDiscoveredAtDesc(userId).stream()
                .map(SearchAlertResponse::from)
                .toList();
    }

    @Transactional
    public void markAllSeen(Jwt jwt) {
        UUID userId = currentUsers.requireUser(jwt).getId();
        matches.findBySavedSearch_User_IdAndSeenFalse(userId)
                .forEach(SearchAlertMatch::markSeen);
    }

    @Transactional
    public void refreshAll() {
        Instant checkedAt = Instant.now();
        List<IndexedJob> activeJobs = indexedJobs.findByActiveTrue();
        for (SavedSearch search : savedSearches.findByAlertsEnabledTrue()) {
            Instant previousCheck = search.getLastCheckedAt() == null
                    ? search.getCreatedAt()
                    : search.getLastCheckedAt();
            activeJobs.stream()
                    .filter(job -> job.getFirstSeenAt().isAfter(previousCheck))
                    .filter(job -> matches(search, job, checkedAt))
                    .filter(job -> !matches.existsBySavedSearch_IdAndIndexedJob_Id(
                            search.getId(), job.getId()))
                    .map(job -> new SearchAlertMatch(search, job))
                    .forEach(matches::save);
            search.checked(checkedAt);
        }
    }

    private boolean matches(SavedSearch search, IndexedJob job, Instant checkedAt) {
        if (search.isEntryLevelOnly() && !job.isEntryLevelLikely()) {
            return false;
        }
        if (search.getOpportunityType() != null
                && search.getOpportunityType() != job.getOpportunityType()) {
            return false;
        }
        if (search.getCareerStage() != null
                && search.getCareerStage() != job.getCareerStage()) {
            return false;
        }
        if (search.getDegreeRequirement() != null
                && search.getDegreeRequirement() != job.getDegreeRequirement()) {
            return false;
        }
        if (search.getSponsorshipStatus() != null
                && search.getSponsorshipStatus() != job.getSponsorshipStatus()) {
            return false;
        }
        if (search.getMaximumExperience() != null
                && job.getExperienceMax() != null
                && job.getExperienceMax() > search.getMaximumExperience()) {
            return false;
        }
        if (!blank(search.getCountryCode())
                && !"ANY".equalsIgnoreCase(search.getCountryCode())
                && !normalized(search.getCountryCode()).equals(normalized(job.getCountryCode()))) {
            return false;
        }
        if (search.getWorkplaceType() != null
                && search.getWorkplaceType() != job.getWorkplaceType()) {
            return false;
        }
        if (!blank(search.getLocation())
                && !searchable(job.getLocation()).contains(normalized(search.getLocation()))) {
            return false;
        }
        if (search.getPostedWithinDays() != null
                && (job.getPublishedAt() == null
                || job.getPublishedAt().isBefore(
                        checkedAt.minus(search.getPostedWithinDays(), ChronoUnit.DAYS)))) {
            return false;
        }
        List<String> terms = terms(search.getQuery());
        if (terms.isEmpty()) {
            return true;
        }
        String content = String.join(" ",
                searchable(job.getTitle()),
                searchable(job.getCompany()),
                searchable(job.getLocation()),
                searchable(job.getDescription()));
        return terms.stream().anyMatch(content::contains);
    }

    private List<String> terms(String query) {
        if (blank(query)) {
            return List.of();
        }
        return List.of(normalized(query).split("[^a-z0-9+#.]+")).stream()
                .filter(term -> term.length() > 1)
                .distinct()
                .toList();
    }

    private String searchable(String value) {
        return normalized(value);
    }

    private String normalized(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
