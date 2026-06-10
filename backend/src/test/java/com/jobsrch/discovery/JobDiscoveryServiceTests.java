package com.jobsrch.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
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
                                "US", WorkplaceType.REMOTE, "Build Java APIs",
                                "https://example.com/1", Instant.now(), null, 0, 2, true),
                        new DiscoveredJob(
                                "2", provider(), companyName, "Staff Designer", "Remote",
                                "US", WorkplaceType.REMOTE, "Lead design",
                                "https://example.com/2", Instant.now(), null, 6, 10, false));
            }
        };
        JobDiscoveryService service = new JobDiscoveryService(
                List.of(client),
                List.of(),
                new JobBoardCatalog(),
                mock(JobIndexService.class));

        List<DiscoveredJob> results = service.search(
                JobProvider.GREENHOUSE,
                "example",
                "Example Co",
                "junior java",
                "remote",
                "US",
                WorkplaceType.REMOTE,
                30,
                DiscoverySort.RELEVANCE,
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
                                "US", WorkplaceType.HYBRID, "Build services",
                                "https://example.com/" + companyIdentifier + "/1",
                                Instant.now().minusSeconds(60), null, null, null, true),
                        new DiscoveredJob(
                                companyIdentifier + "-2", provider(), companyName,
                                "Product Analyst", "Remote",
                                "US", WorkplaceType.REMOTE,
                                "Partner with software developers",
                                "https://example.com/" + companyIdentifier + "/2",
                                Instant.now(), null, null, null, true),
                        new DiscoveredJob(
                                companyIdentifier + "-3", provider(), companyName,
                                "Backend Developer", "Tokyo, Japan",
                                "JP", WorkplaceType.ON_SITE, "Build services",
                                "https://example.com/" + companyIdentifier + "/3",
                                Instant.now(), null, null, null, true));
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
                List.of(),
                new JobBoardCatalog(),
                mock(JobIndexService.class));

        List<DiscoveredJob> results = service.search(
                null,
                null,
                null,
                "junior developer engineer",
                null,
                "US",
                null,
                null,
                DiscoverySort.RELEVANCE,
                true);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).title()).isEqualTo("Backend Developer");
        assertThat(results).extracting(DiscoveredJob::title)
                .contains("Product Analyst");
        assertThat(results).extracting(DiscoveredJob::countryCode)
                .containsOnly("US");
    }

    @Test
    void supportsNewestSortingAndFreshnessFiltering() {
        Instant now = Instant.now();
        JobProviderClient client = new JobProviderClient() {
            @Override
            public JobProvider provider() {
                return JobProvider.LEVER;
            }

            @Override
            public List<DiscoveredJob> fetch(String companyIdentifier, String companyName) {
                return List.of(
                        new DiscoveredJob(
                                "old", provider(), companyName, "Java Engineer", "Austin, TX",
                                "US", WorkplaceType.HYBRID, "Java",
                                "https://example.com/old", now.minusSeconds(40 * 86_400L),
                                null, null, null, true),
                        new DiscoveredJob(
                                "new", provider(), companyName, "Software Engineer", "Remote, US",
                                "US", WorkplaceType.REMOTE, "Java",
                                "https://example.com/new", now.minusSeconds(86_400L),
                                null, null, null, true));
            }
        };
        JobDiscoveryService service = new JobDiscoveryService(
                List.of(client),
                List.of(),
                new JobBoardCatalog(),
                mock(JobIndexService.class));

        List<DiscoveredJob> results = service.search(
                JobProvider.LEVER,
                "example",
                "Example",
                "engineer",
                null,
                "US",
                null,
                30,
                DiscoverySort.NEWEST,
                true);

        assertThat(results).extracting(DiscoveredJob::externalId)
                .containsExactly("new");
    }

    @Test
    void capsResultsFromOneCompanyToKeepTheListDiverse() {
        JobProviderClient client = new JobProviderClient() {
            @Override
            public JobProvider provider() {
                return JobProvider.GREENHOUSE;
            }

            @Override
            public List<DiscoveredJob> fetch(String companyIdentifier, String companyName) {
                List<DiscoveredJob> jobs = new ArrayList<>();
                for (int index = 0; index < 8; index++) {
                    jobs.add(new DiscoveredJob(
                            "dominant-" + index,
                            provider(),
                            "Dominant Co",
                            "Software Engineer " + index,
                            "Remote, US",
                            "US",
                            WorkplaceType.REMOTE,
                            "Build software",
                            "https://example.com/dominant/" + index,
                            Instant.now(),
                            null,
                            0,
                            2,
                            true));
                }
                for (int index = 0; index < 2; index++) {
                    jobs.add(new DiscoveredJob(
                            "other-" + index,
                            provider(),
                            "Other Co",
                            "Software Developer " + index,
                            "Remote, US",
                            "US",
                            WorkplaceType.REMOTE,
                            "Build software",
                            "https://example.com/other/" + index,
                            Instant.now(),
                            null,
                            0,
                            2,
                            true));
                }
                return jobs;
            }
        };
        JobDiscoveryService service = new JobDiscoveryService(
                List.of(client),
                List.of(),
                new JobBoardCatalog(),
                mock(JobIndexService.class));

        List<DiscoveredJob> results = service.search(
                JobProvider.GREENHOUSE,
                "example",
                null,
                null,
                null,
                "US",
                null,
                null,
                DiscoverySort.RELEVANCE,
                true);

        assertThat(results).hasSize(7);
        assertThat(results.stream()
                .filter(job -> job.company().equals("Dominant Co")))
                .hasSize(5);
        assertThat(results.stream()
                .filter(job -> job.company().equals("Other Co")))
                .hasSize(2);
    }
}
