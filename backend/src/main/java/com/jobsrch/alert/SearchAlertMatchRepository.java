package com.jobsrch.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchAlertMatchRepository extends JpaRepository<SearchAlertMatch, UUID> {

    boolean existsBySavedSearch_IdAndIndexedJob_Id(UUID savedSearchId, UUID indexedJobId);

    List<SearchAlertMatch> findBySavedSearch_User_IdOrderByDiscoveredAtDesc(UUID userId);

    List<SearchAlertMatch> findBySavedSearch_User_IdAndSeenFalse(UUID userId);
}
