package com.jobsrch.discovery;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    private static final Pattern US_STATE_CODE_LOCATION = Pattern.compile(
            "(?:,|\\()\\s*(?:" + String.join("|", US_STATE_CODES)
                    + ")(?=\\s|,|\\)|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> COUNTRY_NAMES = countryNames();
    private static final Map<String, String> INTERNATIONAL_CITY_COUNTRIES = Map.ofEntries(
            Map.entry("amsterdam", "NL"),
            Map.entry("bangalore", "IN"),
            Map.entry("bengaluru", "IN"),
            Map.entry("berlin", "DE"),
            Map.entry("dublin", "IE"),
            Map.entry("london", "GB"),
            Map.entry("melbourne", "AU"),
            Map.entry("paris", "FR"),
            Map.entry("sydney", "AU"),
            Map.entry("tbilisi", "GE"),
            Map.entry("tokyo", "JP"),
            Map.entry("toronto", "CA"),
            Map.entry("vancouver", "CA"),
            Map.entry("warsaw", "PL"));

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
            // USAJOBS returns ISO local timestamps without an offset. Treat
            // these consistently as UTC; freshness filtering only requires a
            // stable instant and must not discard every USAJOBS posting.
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException invalidLocalDate) {
                return null;
            }
        }
    }

    /**
     * Infers only high-confidence country codes from provider location text.
     * Unknown locations remain null rather than being silently treated as US.
     */
    static String inferCountryCode(String... values) {
        Set<String> matches = inferCountryCodes(values);
        return matches.size() == 1 ? matches.iterator().next() : null;
    }

    /**
     * Returns every country explicitly supported by the supplied location text.
     * Search filtering uses the full set so mixed-country listings are not
     * presented as exclusively US opportunities.
     */
    static Set<String> inferCountryCodes(String... values) {
        String text = normalize(String.join(" ", nonNull(values)));
        String countryText = text.replaceAll("(?<![a-z])new mexico(?![a-z])", " ");
        Set<String> matches = new LinkedHashSet<>();
        COUNTRY_NAMES.forEach((countryName, countryCode) -> {
            if (containsPhrase(countryText, countryName)) {
                matches.add(countryCode);
            }
        });
        INTERNATIONAL_CITY_COUNTRIES.forEach((city, countryCode) -> {
            if (containsPhrase(text, city)) {
                matches.add(countryCode);
            }
        });
        if (containsPhrase(text, "u.s.") || containsPhrase(text, "usa")) {
            matches.add("US");
        }
        if (containsPhrase(text, "uk")
                || containsPhrase(text, "england")
                || containsPhrase(text, "scotland")
                || containsPhrase(text, "wales")) {
            matches.add("GB");
        }
        if (US_STATE_NAMES.stream().anyMatch(state ->
                containsPhrase(text, state)
                        && !("georgia".equals(state)
                                && containsPhrase(text, "tbilisi")))) {
            matches.add("US");
        }
        if (US_STATE_CODE_LOCATION.matcher(text).find()) {
            matches.add("US");
        }
        return Set.copyOf(matches);
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
                || text.contains("in office") || text.contains("in-office")) {
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

    private static boolean containsPhrase(String text, String phrase) {
        return Pattern.compile(
                "(?<![a-z])" + Pattern.quote(phrase) + "(?![a-z])",
                Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    private static Map<String, String> countryNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (String countryCode : Locale.getISOCountries()) {
            String name = new Locale.Builder()
                    .setRegion(countryCode)
                    .build()
                    .getDisplayCountry(Locale.ENGLISH);
            if (!name.isBlank()) {
                names.put(normalize(name), countryCode);
            }
        }
        // "Georgia" is also a US state; the international city map handles
        // high-confidence Georgian locations such as Tbilisi.
        names.remove("georgia");
        names.put("south korea", "KR");
        names.put("russia", "RU");
        names.put("taiwan", "TW");
        names.put("czech republic", "CZ");
        return Map.copyOf(names);
    }
}
