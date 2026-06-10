package com.jobsrch.alert;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.jobsrch.discovery.WorkplaceType;
import com.jobsrch.discovery.CareerStage;
import com.jobsrch.discovery.DegreeRequirement;
import com.jobsrch.discovery.OpportunityType;
import com.jobsrch.discovery.SponsorshipStatus;
import com.jobsrch.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "saved_searches")
public class SavedSearch {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false)
    private String name;

    @Column(name = "query_text")
    private String query;

    private String location;

    @Column(name = "country_code")
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "workplace_type")
    private WorkplaceType workplaceType;

    @Column(name = "posted_within_days")
    private Integer postedWithinDays;

    @Column(name = "entry_level_only", nullable = false)
    private boolean entryLevelOnly;

    @Enumerated(EnumType.STRING)
    @Column(name = "opportunity_type")
    private OpportunityType opportunityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "career_stage")
    private CareerStage careerStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree_requirement")
    private DegreeRequirement degreeRequirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "sponsorship_status")
    private SponsorshipStatus sponsorshipStatus;

    @Column(name = "maximum_experience")
    private Integer maximumExperience;

    @Column(name = "alerts_enabled", nullable = false)
    private boolean alertsEnabled;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedSearch() {
    }

    public SavedSearch(UserAccount user, SavedSearchRequest request) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.createdAt = Instant.now();
        update(request);
    }

    public void update(SavedSearchRequest request) {
        this.name = request.name().trim();
        this.query = request.query();
        this.location = request.location();
        this.countryCode = request.countryCode();
        this.workplaceType = request.workplaceType();
        this.postedWithinDays = request.postedWithinDays();
        this.entryLevelOnly = request.entryLevelOnly();
        this.opportunityType = request.opportunityType();
        this.careerStage = request.careerStage();
        this.degreeRequirement = request.degreeRequirement();
        this.sponsorshipStatus = request.sponsorshipStatus();
        this.maximumExperience = request.maximumExperience();
        this.alertsEnabled = request.alertsEnabled();
        this.updatedAt = Instant.now();
    }

    public void checked(Instant checkedAt) {
        this.lastCheckedAt = checkedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public String getName() {
        return name;
    }

    public String getQuery() {
        return query;
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

    public Integer getPostedWithinDays() {
        return postedWithinDays;
    }

    public boolean isEntryLevelOnly() {
        return entryLevelOnly;
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

    public Integer getMaximumExperience() {
        return maximumExperience;
    }

    public boolean isAlertsEnabled() {
        return alertsEnabled;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
