package com.jobsrch.discovery;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Interprets common experience phrases and seniority labels from a posting.
 *
 * <p>This classifier is intentionally conservative and explainable. It is not
 * an ML hiring model; it only helps hide clearly senior roles from the default
 * zero-to-three-year view.</p>
 */
@Component
public class ExperienceClassifier {

    private static final Pattern RANGE = Pattern.compile(
            "\\b(\\d{1,2})\\s*(?:-|to)\\s*(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b");
    private static final Pattern SINGLE = Pattern.compile(
            "\\b(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b");
    private static final Pattern ENTRY_MARKER = Pattern.compile(
            "\\b(?:junior|entry[ -]level|associate|new grad|graduate|intern|internship|apprentice)\\b");
    private static final Pattern SENIOR_MARKER = Pattern.compile(
            "(?:\\bsr\\.?(?=\\s|$|[-,/]))"
                    + "|\\b(?:senior|staff|principal|lead|manager|director|vice president|vp)\\b");

    public ExperienceClassification classify(String title, String description) {
        String normalizedTitle = normalize(title);
        String text = normalize(title + " " + nullToEmpty(description)).replace('\u2013', '-');

        Matcher rangeMatcher = RANGE.matcher(text);
        Integer minimum = null;
        Integer maximum = null;
        while (rangeMatcher.find()) {
            int rangeMin = Integer.parseInt(rangeMatcher.group(1));
            int rangeMax = Integer.parseInt(rangeMatcher.group(2));
            minimum = minimum == null ? rangeMin : Math.max(minimum, rangeMin);
            maximum = maximum == null ? rangeMax : Math.max(maximum, rangeMax);
        }

        if (minimum == null) {
            Matcher singleMatcher = SINGLE.matcher(text);
            while (singleMatcher.find()) {
                int years = Integer.parseInt(singleMatcher.group(1));
                minimum = minimum == null ? years : Math.max(minimum, years);
                maximum = minimum;
            }
        }

        boolean explicitEntry = ENTRY_MARKER.matcher(normalizedTitle).find();
        boolean explicitSenior = SENIOR_MARKER.matcher(normalizedTitle).find();
        boolean likelyEntry = !explicitSenior && (
                explicitEntry
                        || maximum == null
                        || maximum <= 3);

        return new ExperienceClassification(minimum, maximum, likelyEntry);
    }

    private String normalize(String value) {
        return nullToEmpty(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record ExperienceClassification(
            Integer minimumYears,
            Integer maximumYears,
            boolean entryLevelLikely) {
    }
}
