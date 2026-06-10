package com.jobsrch.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class JobDiscoveryServiceTests {

    @Test
    void appliesEntryLevelAndTextFiltersAfterProviderMapping() {
        JobProviderClient client = new JobProviderClient() {
            @Override
            public JobProvider provider() {
                return JobProvider.GREENHOUSE;
            }

            @Override
            public List<DiscoveredJob> fetch(String companyIdentifier, String companyName) {
                return List.of(
                        new DiscoveredJob(
                                "1", provider(), companyName, "Junior Java Engineer", "Remote",
                                "Build Java APIs", "https://example.com/1", null, 0, 2, true),
                        new DiscoveredJob(
                                "2", provider(), companyName, "Staff Designer", "Remote",
                                "Lead design", "https://example.com/2", null, 6, 10, false));
            }
        };
        JobDiscoveryService service = new JobDiscoveryService(
                List.of(client),
                new JobBoardCatalog());

        List<DiscoveredJob> results = service.search(
                JobProvider.GREENHOUSE,
                "example",
                "Example Co",
                "junior java",
                "remote",
                true);

        assertThat(results).extracting(DiscoveredJob::title)
                .containsExactly("Junior Java Engineer");
        assertThat(results.get(0).company()).isEqualTo("Example Co");
    }

    @Test
    void catalogSearchUsesAnyRoleTermAndRanksTitleMatchesFirst() {
        JobProviderClient greenhouse = new JobProviderClient() {
            @Override
            public JobProvider provider() {
                return JobProvider.GREENHOUSE;
            }

            @Override
            public List<DiscoveredJob> fetch(String companyIdentifier, String companyName) {
                return List.of(
                        new DiscoveredJob(
                                companyIdentifier + "-1", provider(), companyName,
                                "Backend Developer", "New York",
                                "Build services", "https://example.com/" + companyIdentifier + "/1",
                                null, null, null, true),
                        new DiscoveredJob(
                                companyIdentifier + "-2", provider(), companyName,
                                "Product Analyst", "Remote",
                                "Partner with software developers",
                                "https://example.com/" + companyIdentifier + "/2",
                                null, null, null, true));
            }
        };
        JobProviderClient lever = new JobProviderClient() {
            @Override
            public JobProvider provider() {
                return JobProvider.LEVER;
            }

            @Override
            public List<DiscoveredJob> fetch(String companyIdentifier, String companyName) {
                return List.of();
            }
        };
        JobDiscoveryService service = new JobDiscoveryService(
                List.of(greenhouse, lever),
                new JobBoardCatalog());

        List<DiscoveredJob> results = service.search(
                null, null, null, "junior developer engineer", null, true);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).isEqualTo("Backend Developer");
        assertThat(results).extracting(DiscoveredJob::title)
                .contains("Product Analyst");
    }
}
