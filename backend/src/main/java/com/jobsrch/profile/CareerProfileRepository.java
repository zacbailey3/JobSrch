package com.jobsrch.profile;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {
}
