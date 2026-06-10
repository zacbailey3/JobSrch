package com.jobsrch.discovery;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class LeverJobProviderClient implements JobProviderClient {

    private final RestClient client;
    private final ExperienceClassifier classifier;

    public LeverJobProviderClient(ExperienceClassifier classifier) {
        this.classifier = classifier;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .baseUrl("https://api.lever.co")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public JobProvider provider() {
        return JobProvider.LEVER;
    }

    @Override
    public List<DiscoveredJob> fetch(String companyIdentifier, String companyName) {
        String site = ProviderSupport.validateIdentifier(companyIdentifier);
        try {
            LeverJob[] response = client.get()
                    .uri("/v0/postings/{site}?mode=json", site)
                    .retrieve()
                    .body(LeverJob[].class);
            if (response == null) {
                return List.of();
            }
            return Arrays.stream(response)
                    .map(job -> toDiscoveredJob(job, companyName))
                    .toList();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    BAD_GATEWAY, "Lever site could not be loaded", exception);
        }
    }

    private DiscoveredJob toDiscoveredJob(LeverJob job, String companyName) {
        String description = job.descriptionPlain() == null
                ? ProviderSupport.plainText(job.description())
                : job.descriptionPlain();
        ExperienceClassifier.ExperienceClassification experience =
                classifier.classify(job.text(), description);
        return new DiscoveredJob(
                job.id(),
                provider(),
                companyName,
                job.text(),
                job.categories() == null ? null : job.categories().location(),
                description,
                job.hostedUrl(),
                job.createdAt() == null ? null : Instant.ofEpochMilli(job.createdAt()),
                experience.minimumYears(),
                experience.maximumYears(),
                experience.entryLevelLikely());
    }

    private record LeverJob(
            String id,
            String text,
            LeverCategories categories,
            String description,
            @JsonProperty("descriptionPlain") String descriptionPlain,
            @JsonProperty("hostedUrl") String hostedUrl,
            @JsonProperty("createdAt") Long createdAt) {
    }

    private record LeverCategories(String location) {
    }
}
