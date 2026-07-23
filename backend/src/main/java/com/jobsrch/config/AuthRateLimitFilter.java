package com.jobsrch.config;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Small single-instance rate limiter for abuse-prone public account endpoints.
 * A distributed deployment should replace this map with a shared Redis-backed
 * limiter so every application instance observes the same counters.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60;
    private static final Map<String, Integer> LIMITS = Map.of(
            "/api/auth/login", 10,
            "/api/auth/register", 5,
            "/api/auth/password-reset/request", 5,
            "/api/auth/password-reset/confirm", 10);

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITS.containsKey(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        String key = request.getRemoteAddr() + ":" + request.getServletPath();
        int limit = LIMITS.get(request.getServletPath());
        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startedAt >= WINDOW_SECONDS) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt, existing.count + 1);
        });

        if (current.count > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", Long.toString(
                    Math.max(1, WINDOW_SECONDS - (now - current.startedAt))));
            response.getWriter().write(
                    "{\"title\":\"Too Many Requests\","
                            + "\"status\":429,"
                            + "\"detail\":\"Please wait before trying again.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record Window(long startedAt, int count) {
    }
}
