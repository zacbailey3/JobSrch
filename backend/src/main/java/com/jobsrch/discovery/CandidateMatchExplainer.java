package com.jobsrch.discovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.jobsrch.profile.ProfileResponse;

/**
 * Adds candidate-specific context without turning suitability into a hidden
 * score. Every explanation is traceable to profile text or posting metadata.
 */
@Component
public class CandidateMatchExplainer {

    public DiscoveredJob explain(DiscoveredJob job, ProfileResponse profile) {
        if (profile == null) {
            return job;
        }
        Set<String> reasons = new LinkedHashSet<>(job.matchReasons());
        Set<String> cautions = new LinkedHashSet<>(job.cautions());
        String searchableJob = normalize(job.title() + " " + job.description());

        List<String> matchingSkills = splitList(profile.skills()).stream()
                .filter(skill -> searchableJob.contains(normalize(skill)))
                .limit(4)
                .toList();
        if (!matchingSkills.isEmpty()) {
            reasons.add("Matches your profile skills: " + String.join(", ", matchingSkills) + ".");
        }

        List<String> matchingRoles = splitList(profile.desiredRoles()).stream()
                .filter(role -> normalize(job.title()).contains(normalize(role)))
                .limit(2)
                .toList();
        if (!matchingRoles.isEmpty()) {
            reasons.add("The title aligns with your desired role: "
                    + String.join(", ", matchingRoles) + ".");
        }

        Integer years = profile.yearsExperience();
        if (years != null && job.experienceMin() != null) {
            if (years < job.experienceMin()) {
                cautions.add("Your profile lists " + experienceLabel(years) + "; "
                        + "the posting appears to ask for at least " + job.experienceMin() + ".");
            } else if (job.experienceMax() == null || years <= job.experienceMax()) {
                reasons.add("Your profile's " + experienceLabel(years)
                        + " fits the stated range.");
            }
        }

        if (requiresDegree(job.degreeRequirement())) {
            if (hasMatchingDegree(profile.education(), job.degreeRequirement())) {
                reasons.add("Your profile education appears to meet the stated degree level.");
                cautions.remove("A bachelor's degree appears to be required.");
                cautions.remove("An advanced degree appears to be required.");
            }
        }

        return job.withGuidance(List.copyOf(reasons), List.copyOf(cautions));
    }

    private boolean requiresDegree(DegreeRequirement requirement) {
        return requirement == DegreeRequirement.BACHELORS_REQUIRED
                || requirement == DegreeRequirement.ADVANCED_DEGREE_REQUIRED;
    }

    private boolean hasMatchingDegree(String education, DegreeRequirement requirement) {
        String normalized = normalize(education);
        boolean bachelors = normalized.contains("bachelor")
                || normalized.contains("b.s.")
                || normalized.contains("bs ")
                || normalized.contains("b.a.")
                || normalized.contains("ba ");
        boolean advanced = normalized.contains("master")
                || normalized.contains("m.s.")
                || normalized.contains("ms ")
                || normalized.contains("phd")
                || normalized.contains("ph.d");
        return requirement == DegreeRequirement.BACHELORS_REQUIRED
                ? bachelors || advanced
                : advanced;
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Arrays.stream(value.split("[,;\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .forEach(values::add);
        return values;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String experienceLabel(int years) {
        return years + (years == 1 ? " year of experience" : " years of experience");
    }
}
