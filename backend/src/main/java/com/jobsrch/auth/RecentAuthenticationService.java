package com.jobsrch.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.AccountSecurityProperties;

@Service
public class RecentAuthenticationService {

    private final AccountSecurityProperties properties;
    private final Clock clock = Clock.systemUTC();

    public RecentAuthenticationService(AccountSecurityProperties properties) {
        this.properties = properties;
    }

    public Instant requireRecent(Jwt jwt) {
        Instant authenticatedAt = authenticatedAt(jwt);
        Instant now = Instant.now(clock);
        if (authenticatedAt == null
                || authenticatedAt.isAfter(now.plusSeconds(30))
                || authenticatedAt.plus(properties.recentAuthenticationWindow()).isBefore(now)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Confirm your password again before changing sensitive account settings");
        }
        return authenticatedAt;
    }

    public Instant authenticatedAt(Jwt jwt) {
        Object claim = jwt.getClaim("authTime");
        return claim instanceof Number seconds ? Instant.ofEpochSecond(seconds.longValue()) : null;
    }

    public Instant recentAuthenticationExpiresAt(Jwt jwt) {
        Instant authenticatedAt = authenticatedAt(jwt);
        return authenticatedAt == null
                ? null
                : authenticatedAt.plus(properties.recentAuthenticationWindow());
    }
}
