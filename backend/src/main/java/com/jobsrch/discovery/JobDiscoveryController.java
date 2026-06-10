package com.jobsrch.discovery;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/discovery")
public class JobDiscoveryController {

    private final JobDiscoveryService discoveryService;

    public JobDiscoveryController(JobDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    List<DiscoveredJob> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) JobProvider provider,
            @RequestParam(required = false) String companyIdentifier,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "US") String countryCode,
            @RequestParam(required = false) WorkplaceType workplaceType,
            @RequestParam(required = false) Integer postedWithinDays,
            @RequestParam(defaultValue = "RELEVANCE") DiscoverySort sort,
            @RequestParam(defaultValue = "true") boolean entryLevelOnly,
            @RequestParam(required = false) OpportunityType opportunityType,
            @RequestParam(required = false) CareerStage careerStage,
            @RequestParam(required = false) DegreeRequirement degreeRequirement,
            @RequestParam(required = false) SponsorshipStatus sponsorshipStatus,
            @RequestParam(required = false) Integer maximumExperience) {
        return discoveryService.search(
                jwt,
                provider,
                companyIdentifier,
                companyName,
                query,
                location,
                countryCode,
                workplaceType,
                postedWithinDays,
                sort,
                entryLevelOnly,
                opportunityType,
                careerStage,
                degreeRequirement,
                sponsorshipStatus,
                maximumExperience);
    }
}
