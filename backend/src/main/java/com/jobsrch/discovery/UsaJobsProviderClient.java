package com.jobsrch.discovery;

import static org.springframework.http.HttpHeaders.USER_AGENT;

import java.time.Duration;
import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobsrch.config.UsaJobsProperties;

@Component
public class UsaJobsProviderClient implements AggregateJobProviderClient {

    private final UsaJobsProperties properties;
    private final ExperienceClassifier classifier;
    private final RestClient client;

    public UsaJobsProviderClient(
            UsaJobsProperties properties,
            ExperienceClassifier classifier) {
        this.properties = properties;
        this.classifier = classifier;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder()
                .baseUrl("https://data.usajobs.gov")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public JobProvider provider() {
        return JobProvider.USAJOBS;
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
            UsaJobsResponse response = client.get()
                    .uri(builder -> {
                        builder.path("/api/Search")
                                .queryParam("ResultsPerPage", 100)
                                .queryParam("WhoMayApply", "public")
                                .queryParam("HiringPath", "public;student;graduates")
                                .queryParam("Fields", "Full");
                        if (query != null && !query.isBlank()) {
                            builder.queryParam("Keyword", query.trim());
                        }
                        if (location != null && !location.isBlank()) {
                            builder.queryParam("LocationName", location.trim());
                        }
                        if (postedWithinDays != null) {
                            builder.queryParam("DatePosted", postedWithinDays);
                        }
                        return builder.build();
                    })
                    .header(USER_AGENT, properties.email())
                    .header("Authorization-Key", properties.apiKey())
                    .retrieve()
                    .body(UsaJobsResponse.class);
            if (response == null || response.searchResult() == null
                    || response.searchResult().items() == null) {
                return List.of();
            }
            return response.searchResult().items().stream()
                    .map(this::map)
                    .toList();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "USAJOBS is unavailable", exception);
        }
    }

    private DiscoveredJob map(SearchResultItem item) {
        Descriptor job = item.descriptor();
        String description = String.join(" ",
                value(job.qualificationSummary()),
                job.userArea() == null || job.userArea().details() == null
                        ? ""
                        : value(job.userArea().details().jobSummary()));
        ExperienceClassifier.ExperienceClassification experience =
                classifier.classify(job.positionTitle(), description);
        String location = job.positionLocationDisplay();
        WorkplaceType workplace = ProviderSupport.inferWorkplaceType(location, description);
        return new DiscoveredJob(
                item.matchedObjectId(),
                provider(),
                value(job.organizationName()),
                value(job.positionTitle()),
                location,
                "US",
                workplace,
                description,
                job.positionUri(),
                ProviderSupport.parseInstant(job.publicationStartDate()),
                ProviderSupport.parseInstant(job.applicationCloseDate()),
                experience.minimumYears(),
                experience.maximumYears(),
                experience.entryLevelLikely());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record UsaJobsResponse(
            @JsonProperty("SearchResult") SearchResult searchResult) {
    }

    private record SearchResult(
            @JsonProperty("SearchResultItems") List<SearchResultItem> items) {
    }

    private record SearchResultItem(
            @JsonProperty("MatchedObjectId") String matchedObjectId,
            @JsonProperty("MatchedObjectDescriptor") Descriptor descriptor) {
    }

    private record Descriptor(
            @JsonProperty("PositionTitle") String positionTitle,
            @JsonProperty("PositionURI") String positionUri,
            @JsonProperty("PositionLocationDisplay") String positionLocationDisplay,
            @JsonProperty("OrganizationName") String organizationName,
            @JsonProperty("QualificationSummary") String qualificationSummary,
            @JsonProperty("PublicationStartDate") String publicationStartDate,
            @JsonProperty("ApplicationCloseDate") String applicationCloseDate,
            @JsonProperty("UserArea") UserArea userArea) {
    }

    private record UserArea(@JsonProperty("Details") Details details) {
    }

    private record Details(@JsonProperty("JobSummary") String jobSummary) {
    }
}
