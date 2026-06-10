package com.jobsrch.discovery;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "indexed_jobs")
public class IndexedJob {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @Column(name = "source_key", nullable = false, unique = true, length = 64)
    private String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobProvider provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String location;

    @Column(name = "country_code")
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "workplace_type", nullable = false)
    private WorkplaceType workplaceType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_url", nullable = false, length = 1200)
    private String sourceUrl;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "experience_min")
    private Integer experienceMin;

    @Column(name = "experience_max")
    private Integer experienceMax;

    @Column(name = "entry_level_likely", nullable = false)
    private boolean entryLevelLikely;

    @Enumerated(EnumType.STRING)
    @Column(name = "opportunity_type", nullable = false)
    private OpportunityType opportunityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "career_stage", nullable = false)
    private CareerStage careerStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree_requirement", nullable = false)
    private DegreeRequirement degreeRequirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "sponsorship_status", nullable = false)
    private SponsorshipStatus sponsorshipStatus;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected IndexedJob() {
    }

    public IndexedJob(String sourceKey, DiscoveredJob job, Instant seenAt) {
        this.id = UUID.randomUUID();
        this.sourceKey = sourceKey;
        this.firstSeenAt = seenAt;
        update(job, seenAt);
    }

    public void update(DiscoveredJob job, Instant seenAt) {
        this.provider = job.provider();
        this.externalId = bounded(job.externalId(), 255);
        this.company = bounded(job.company(), 200);
        this.title = bounded(job.title(), 240);
        this.location = bounded(job.location(), 1000);
        this.countryCode = bounded(job.countryCode(), 10);
        this.workplaceType = job.workplaceType();
        this.description = job.description();
        this.sourceUrl = bounded(job.sourceUrl(), 1200);
        this.publishedAt = job.publishedAt();
        this.expiresAt = job.expiresAt();
        this.experienceMin = job.experienceMin();
        this.experienceMax = job.experienceMax();
        this.entryLevelLikely = job.entryLevelLikely();
        this.opportunityType = job.opportunityType();
        this.careerStage = job.careerStage();
        this.degreeRequirement = job.degreeRequirement();
        this.sponsorshipStatus = job.sponsorshipStatus();
        this.active = true;
        this.lastSeenAt = seenAt;
    }

    /**
     * Provider APIs occasionally return display fields longer than the shared
     * schema. Truncating only persisted metadata keeps one unusual posting from
     * rolling back an otherwise useful import or interactive search.
     */
    private String bounded(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }

    public void expire() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public JobProvider getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public WorkplaceType getWorkplaceType() {
        return workplaceType;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Integer getExperienceMin() {
        return experienceMin;
    }

    public Integer getExperienceMax() {
        return experienceMax;
    }

    public boolean isEntryLevelLikely() {
        return entryLevelLikely;
    }

    public OpportunityType getOpportunityType() {
        return opportunityType;
    }

    public CareerStage getCareerStage() {
        return careerStage;
    }

    public DegreeRequirement getDegreeRequirement() {
        return degreeRequirement;
    }

    public SponsorshipStatus getSponsorshipStatus() {
        return sponsorshipStatus;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public DiscoveredJob toDiscoveredJob() {
        return new DiscoveredJob(
                externalId,
                provider,
                company,
                title,
                location,
                countryCode,
                workplaceType,
                description,
                sourceUrl,
                publishedAt,
                expiresAt,
                experienceMin,
                experienceMax,
                entryLevelLikely,
                opportunityType,
                careerStage,
                degreeRequirement,
                sponsorshipStatus,
                lastSeenAt,
                java.util.List.of(),
                java.util.List.of());
    }
}
