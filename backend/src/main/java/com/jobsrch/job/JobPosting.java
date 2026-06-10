package com.jobsrch.job;

import java.time.Instant;
import java.util.UUID;

import com.jobsrch.user.UserAccount;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_postings")
public class JobPosting {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private UserAccount owner;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "experience_min")
    private Integer experienceMin;

    @Column(name = "experience_max")
    private Integer experienceMax;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobPosting() {
    }

    public JobPosting(UserAccount owner, JobRequest request) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.createdAt = Instant.now();
        update(request);
    }

    public void update(JobRequest request) {
        this.company = request.company().trim();
        this.title = request.title().trim();
        this.location = request.location();
        this.description = request.description();
        this.sourceUrl = request.sourceUrl();
        this.experienceMin = request.experienceMin();
        this.experienceMax = request.experienceMax();
        this.publishedAt = request.publishedAt();
    }

    public UUID getId() {
        return id;
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

    public String getDescription() {
        return description;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Integer getExperienceMin() {
        return experienceMin;
    }

    public Integer getExperienceMax() {
        return experienceMax;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
