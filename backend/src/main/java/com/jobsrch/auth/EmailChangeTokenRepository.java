package com.jobsrch.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, UUID> {

    Optional<EmailChangeToken> findByTokenHash(String tokenHash);

    void deleteAllByUserId(UUID userId);
}
