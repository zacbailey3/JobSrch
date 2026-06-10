package com.jobsrch.alert;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.jobsrch.discovery.IndexedJob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "search_alert_matches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"saved_search_id", "indexed_job_id"}))
public class SearchAlertMatch {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_search_id")
    private SavedSearch savedSearch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indexed_job_id")
    private IndexedJob indexedJob;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(nullable = false)
    private boolean seen;

    protected SearchAlertMatch() {
    }

    public SearchAlertMatch(SavedSearch savedSearch, IndexedJob indexedJob) {
        this.id = UUID.randomUUID();
        this.savedSearch = savedSearch;
        this.indexedJob = indexedJob;
        this.discoveredAt = Instant.now();
        this.seen = false;
    }

    public void markSeen() {
        this.seen = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSavedSearchId() {
        return savedSearch.getId();
    }

    public String getSavedSearchName() {
        return savedSearch.getName();
    }

    public IndexedJob getIndexedJob() {
        return indexedJob;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public boolean isSeen() {
        return seen;
    }
}
