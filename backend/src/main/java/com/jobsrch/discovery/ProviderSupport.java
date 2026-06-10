package com.jobsrch.discovery;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

final class ProviderSupport {

    private static final Set<String> US_STATE_CODES = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC");
    private static final Set<String> US_STATE_NAMES = Set.of(
            "alabama", "alaska", "arizona", "arkansas", "california", "colorado",
            "connecticut", "delaware", "florida", "georgia", "hawaii", "idaho",
            "illinois", "indiana", "iowa", "kansas", "kentucky", "louisiana",
            "maine", "maryland", "massachusetts", "michigan", "minnesota",
            "mississippi", "missouri", "montana", "nebraska", "nevada",
            "new hampshire", "new jersey", "new mexico", "new york",
            "north carolina", "north dakota", "ohio", "oklahoma", "oregon",
            "pennsylvania", "rhode island", "south carolina", "south dakota",
            "tennessee", "texas", "utah", "vermont", "virginia", "washington",
            "west virginia", "wisconsin", "wyoming", "district of columbia");

    private ProviderSupport() {
    }

    /**
     * Restricts path input to a provider's board token rather than accepting a
     * user-controlled URL. Provider clients always use their fixed API host.
     */
    static String validateIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Company identifier may contain only letters, numbers, hyphens, and underscores");
        }
        return identifier;
    }

    static String plainText(String html) {
        if (html == null) {
            return "";
        }
        String decoded = HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(html));
        return decoded
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Infers only high-confidence country codes from provider location text.
     * Unknown locations remain null rather than being silently treated as US.
     */
    static String inferCountryCode(String... values) {
        String text = normalize(String.join(" ", nonNull(values)));
        Set<String> matches = new LinkedHashSet<>();
        if (text.contains("united states") || text.contains("u.s.")
                || text.matches(".*\\busa\\b.*")) {
            matches.add("US");
        }
        if (text.contains("canada")) {
            matches.add("CA");
        }
        if (text.contains("united kingdom") || text.contains("england")
                || text.contains("scotland") || text.contains("wales")) {
            matches.add("GB");
        }
        if (text.contains("australia")) {
            matches.add("AU");
        }
        if (text.contains("japan")) {
            matches.add("JP");
        }
        if (text.contains("india")) {
            matches.add("IN");
        }
        if (text.contains("germany")) {
            matches.add("DE");
        }
        if (text.contains("france")) {
            matches.add("FR");
        }
        if (text.contains("singapore")) {
            matches.add("SG");
        }
        if (US_STATE_NAMES.stream().anyMatch(text::contains)) {
            matches.add("US");
        }
        for (String token : text.toUpperCase(Locale.ROOT).split("[^A-Z]+")) {
            if (US_STATE_CODES.contains(token)) {
                matches.add("US");
            }
        }
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    static WorkplaceType inferWorkplaceType(String... values) {
        String text = normalize(String.join(" ", nonNull(values)));
        if (text.contains("hybrid")) {
            return WorkplaceType.HYBRID;
        }
        if (text.contains("remote") || text.contains("work from home")) {
            return WorkplaceType.REMOTE;
        }
        if (text.contains("on-site") || text.contains("onsite")
                || text.contains("in office")) {
            return WorkplaceType.ON_SITE;
        }
        return WorkplaceType.UNKNOWN;
    }

    private static String[] nonNull(String[] values) {
        for (int index = 0; index < values.length; index++) {
            values[index] = values[index] == null ? "" : values[index];
        }
        return values;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
