package com.jobsrch.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTests {

    @Test
    void limitsDirectBoardSearchesWithoutLimitingEveryDiscoveryRequestToThatThreshold()
            throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC));
        AtomicInteger accepted = new AtomicInteger();

        MockHttpServletResponse last = null;
        for (int attempt = 1; attempt <= 11; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/discovery");
            request.setServletPath("/api/discovery");
            request.setRemoteAddr("203.0.113.10");
            request.setParameter("companyIdentifier", "example");
            last = new MockHttpServletResponse();
            filter.doFilter(request, last, (ignoredRequest, ignoredResponse) -> accepted.incrementAndGet());
        }

        assertThat(accepted).hasValue(10);
        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getHeader("Retry-After")).isEqualTo("60");
        assertThat(filter.bucketCount()).isEqualTo(1);
    }
}
