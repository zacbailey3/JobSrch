package com.jobsrch.discovery;

import java.time.Duration;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobsrch.config.AdzunaProperties;

@Component
public class AdzunaProviderClient implements AggregateJobProviderClient {

    private final AdzunaProperties properties;
    private final ExperienceClassifier classifier;
    private final RestClient client;

    public AdzunaProviderClient(
            AdzunaProperties properties,
            ExperienceClassifier classifier) {
        this.properties = properties;
        this.classifier = classifier;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder()
                .baseUrl("https://api.adzuna.com")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public JobProvider provider() {
        return JobProvider.ADZUNA;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public List<DiscoveredJob> search(
            String query,
            String location,
            Integer postedWithinDays) {
        if (!enabled()) {
            return List.of();
        }
        try {
            AdzunaResponse response = client.get()
                    .uri(builder -> {
                        builder.path("/v1/api/jobs/us/search/1")
                                .queryParam("app_id", properties.appId())
                                .queryParam("app_key", properties.appKey())
                                .queryParam("results_per_page", 100)
                                .queryParam("sort_by", "date");
                        if (query != null && !query.isBlank()) {
                            builder.queryParam("what", query.trim());
                        }
                        if (location != null && !location.isBlank()) {
                            builder.queryParam("where", location.trim());
                        }
                        if (postedWithinDays != null) {
                            builder.queryParam("max_days_old", postedWithinDays);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(AdzunaResponse.class);
            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results().stream().map(this::map).toList();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Adzuna is unavailable", exception);
        }
    }

    private DiscoveredJob map(AdzunaJob job) {
        ExperienceClassifier.ExperienceClassification experience =
                classifier.classify(job.title(), job.description());
        String location = job.location() == null ? null : job.location().displayName();
        return new DiscoveredJob(
                job.id(),
                provider(),
                job.company() == null ? "Employer not listed" : job.company().displayName(),
                job.title(),
                location,
                "US",
                ProviderSupport.inferWorkplaceType(location, job.description()),
                value(job.description()),
                job.redirectUrl(),
                ProviderSupport.parseInstant(job.created()),
                null,
                experience.minimumYears(),
                experience.maximumYears(),
                experience.entryLevelLikely());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record AdzunaResponse(List<AdzunaJob> results) {
    }

    private record AdzunaJob(
            String id,
            String title,
            String description,
            String created,
            @JsonProperty("redirect_url") String redirectUrl,
            AdzunaCompany company,
            AdzunaLocation location) {
    }

    private record AdzunaCompany(
            @JsonProperty("display_name") String displayName) {
    }

    private record AdzunaLocation(
            @JsonProperty("display_name") String displayName) {
    }
}
