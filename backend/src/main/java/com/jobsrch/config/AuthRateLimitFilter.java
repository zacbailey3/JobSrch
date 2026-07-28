package com.jobsrch.config;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bounded, single-instance rate limiter for abuse-prone and expensive routes.
 *
 * <p>In production Tomcat is configured to accept forwarded addresses only
 * from internal proxies, so {@link HttpServletRequest#getRemoteAddr()} is the
 * original client address without trusting arbitrary public headers. A
 * multi-replica deployment should replace this implementation with a shared
 * Redis-backed limiter.</p>
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_BUCKETS = 10_000;
    private static final long CLEANUP_INTERVAL = 128;
    private static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/api/auth/login", 10, false),
            new Rule("register", "POST", "/api/auth/register", 5, false),
            new Rule("reset-request", "POST", "/api/auth/password-reset/request", 5, false),
            new Rule("reset-confirm", "POST", "/api/auth/password-reset/confirm", 10, false),
            new Rule("resume-upload", "POST", "/api/profile/resumes", 10, false),
            new Rule("resume-analysis", "POST", "/api/resume-analysis", 10, false),
            new Rule("direct-board-search", "GET", "/api/discovery", 10, true),
            new Rule("discovery-search", "GET", "/api/discovery", 60, false));

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private final Clock clock;

    public AuthRateLimitFilter() {
        this(Clock.systemUTC());
    }

    AuthRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return matchingRule(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Rule rule = matchingRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = Instant.now(clock).getEpochSecond();
        if (requestCount.incrementAndGet() % CLEANUP_INTERVAL == 0 || windows.size() >= MAX_BUCKETS) {
            removeExpired(now);
        }

        String key = rule.name + ":" + actorKey(request);
        if (!windows.containsKey(key) && windows.size() >= MAX_BUCKETS) {
            reject(response, WINDOW_SECONDS);
            return;
        }

        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startedAt >= WINDOW_SECONDS) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt, existing.count + 1);
        });

        if (current.count > rule.limit) {
            reject(response, Math.max(1, WINDOW_SECONDS - (now - current.startedAt)));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Rule matchingRule(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getServletPath();
        return RULES.stream()
                .filter(rule -> rule.method.equals(method) && rule.path.equals(path))
                .filter(rule -> !rule.directBoardOnly
                        || hasText(request.getParameter("companyIdentifier")))
                .findFirst()
                .orElse(null);
    }

    private String actorKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            return "user:" + authentication.getName().toLowerCase(Locale.ROOT);
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void removeExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_SECONDS);
    }

    private void reject(HttpServletResponse response, long retryAfter) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(
                "{\"title\":\"Too Many Requests\",\"status\":429,"
                        + "\"detail\":\"Please wait before trying again.\"}");
    }

    int bucketCount() {
        return windows.size();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Rule(
            String name,
            String method,
            String path,
            int limit,
            boolean directBoardOnly) {
    }

    private record Window(long startedAt, int count) {
    }
}
