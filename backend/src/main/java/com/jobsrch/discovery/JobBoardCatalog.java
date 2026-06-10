package com.jobsrch.discovery;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Small starter catalog used when a candidate searches by role instead of by
 * a specific company board token.
 *
 * <p>The catalog is intentionally explicit: each entry points to a public API
 * supported by an existing provider adapter. Adding a board here expands the
 * default search without granting users control over outbound hostnames.</p>
 */
@Component
public class JobBoardCatalog {

    private static final List<JobBoard> BOARDS = List.of(
            new JobBoard(JobProvider.GREENHOUSE, "stripe", "Stripe"),
            new JobBoard(JobProvider.GREENHOUSE, "cloudflare", "Cloudflare"),
            new JobBoard(JobProvider.GREENHOUSE, "airbnb", "Airbnb"),
            new JobBoard(JobProvider.GREENHOUSE, "figma", "Figma"),
            new JobBoard(JobProvider.GREENHOUSE, "plaid", "Plaid"),
            new JobBoard(JobProvider.GREENHOUSE, "reddit", "Reddit"),
            new JobBoard(JobProvider.GREENHOUSE, "duolingo", "Duolingo"),
            new JobBoard(JobProvider.GREENHOUSE, "databricks", "Databricks"),
            new JobBoard(JobProvider.GREENHOUSE, "robinhood", "Robinhood"),
            new JobBoard(JobProvider.GREENHOUSE, "discord", "Discord"),
            new JobBoard(JobProvider.LEVER, "spotify", "Spotify"),
            new JobBoard(JobProvider.LEVER, "palantir", "Palantir"),
            new JobBoard(JobProvider.LEVER, "netflix", "Netflix"),
            new JobBoard(JobProvider.LEVER, "anduril", "Anduril"),
            new JobBoard(JobProvider.LEVER, "highspot", "Highspot"),
            new JobBoard(JobProvider.LEVER, "verkada", "Verkada"));

    public List<JobBoard> list(JobProvider provider) {
        if (provider == null) {
            return BOARDS;
        }
        return BOARDS.stream()
                .filter(board -> board.provider() == provider)
                .toList();
    }

    public record JobBoard(
            JobProvider provider,
            String identifier,
            String companyName) {
    }
}
