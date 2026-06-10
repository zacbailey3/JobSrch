package com.jobsrch.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.jobsrch.job.JobPosting;
import com.jobsrch.user.UserAccount;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "applied_at")
    private LocalDate appliedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {
    }

    public JobApplication(UserAccount user, JobPosting jobPosting, ApplicationRequest request) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.jobPosting = jobPosting;
        this.createdAt = Instant.now();
        update(jobPosting, request);
    }

    public void update(JobPosting jobPosting, ApplicationRequest request) {
        this.jobPosting = jobPosting;
        this.company = request.company().trim();
        this.title = request.title().trim();
        this.sourceUrl = request.sourceUrl();
        this.status = request.status();
        this.appliedAt = request.appliedAt();
        this.notes = request.notes();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobPostingId() {
        return jobPosting == null ? null : jobPosting.getId();
    }

    public String getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
