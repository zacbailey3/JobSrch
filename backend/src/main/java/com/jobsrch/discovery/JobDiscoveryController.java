package com.jobsrch.discovery;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
public class JobDiscoveryController {

    private final JobDiscoveryService discoveryService;

    public JobDiscoveryController(JobDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    List<DiscoveredJob> search(
            @RequestParam(required = false) JobProvider provider,
            @RequestParam(required = false) String companyIdentifier,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "true") boolean entryLevelOnly) {
        return discoveryService.search(
                provider,
                companyIdentifier,
                companyName,
                query,
                location,
                entryLevelOnly);
    }
}
