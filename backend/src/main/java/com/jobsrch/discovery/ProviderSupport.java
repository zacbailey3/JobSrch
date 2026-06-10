package com.jobsrch.discovery;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

final class ProviderSupport {

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
}
