package com.jobsrch.discovery;

import java.time.Duration;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Component
public class GreenhouseJobProviderClient implements JobProviderClient {

    private final RestClient client;
    private final ExperienceClassifier classifier;

    public GreenhouseJobProviderClient(ExperienceClassifier classifier) {
        this.classifier = classifier;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .baseUrl("https://boards-api.greenhouse.io")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public JobProvider provider() {
        return JobProvider.GREENHOUSE;
    }

    @Override
    public List<DiscoveredJob> fetch(String companyIdentifier, String companyName) {
        String boardToken = ProviderSupport.validateIdentifier(companyIdentifier);
        try {
            GreenhouseResponse response = client.get()
                    .uri("/v1/boards/{boardToken}/jobs?content=true", boardToken)
                    .retrieve()
                    .body(GreenhouseResponse.class);
            if (response == null || response.jobs() == null) {
                return List.of();
            }
            return response.jobs().stream()
                    .map(job -> toDiscoveredJob(job, companyName))
                    .toList();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    BAD_GATEWAY, "Greenhouse board could not be loaded", exception);
        }
    }

    private DiscoveredJob toDiscoveredJob(GreenhouseJob job, String companyName) {
        String description = ProviderSupport.plainText(job.content());
        String location = job.location() == null ? null : job.location().name();
        String officeLocations = job.offices() == null
                ? ""
                : job.offices().stream()
                        .map(GreenhouseOffice::location)
                        .filter(value -> value != null && !value.isBlank())
                        .reduce("", (left, right) -> left + " " + right);
        ExperienceClassifier.ExperienceClassification experience =
                classifier.classify(job.title(), description);
        String countryCode = ProviderSupport.inferCountryCode(location);
        if (countryCode == null) {
            countryCode = ProviderSupport.inferCountryCode(officeLocations);
        }
        return new DiscoveredJob(
                Long.toString(job.id()),
                provider(),
                companyName,
                job.title(),
                location,
                countryCode,
                ProviderSupport.inferWorkplaceType(location, description),
                description,
                job.absoluteUrl(),
                ProviderSupport.parseInstant(job.updatedAt()),
                null,
                experience.minimumYears(),
                experience.maximumYears(),
                experience.entryLevelLikely());
    }

    private record GreenhouseResponse(List<GreenhouseJob> jobs) {
    }

    private record GreenhouseJob(
            long id,
            String title,
            GreenhouseLocation location,
            String content,
            List<GreenhouseOffice> offices,
            @JsonProperty("absolute_url") String absoluteUrl,
            @JsonProperty("updated_at") String updatedAt) {
    }

    private record GreenhouseLocation(String name) {
    }

    private record GreenhouseOffice(String location) {
    }
}
