package com.jobsrch.alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {

    List<SavedSearch> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<SavedSearch> findByIdAndUser_Id(UUID id, UUID userId);

    List<SavedSearch> findByAlertsEnabledTrue();

    void deleteAllByUser_Id(UUID userId);
}
