package com.jobsrch.user;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {

    private final UserAccountRepository users;

    public CurrentUserService(UserAccountRepository users) {
        this.users = users;
    }

    /**
     * Resolves the database account represented by the JWT subject.
     *
     * <p>Services use this method before repository queries so all domain
     * operations are scoped to the authenticated account.</p>
     */
    public UserAccount requireUser(Jwt jwt) {
        UserAccount user = resolveUser(jwt);
        Object claim = jwt.getClaim("securityVersion");
        if (!(claim instanceof Number version)
                || version.longValue() != user.getSecurityVersion()) {
            throw invalidSession();
        }
        return user;
    }

    private UserAccount resolveUser(Jwt jwt) {
        try {
            return users.findById(UUID.fromString(jwt.getSubject()))
                    .orElseThrow(this::invalidSession);
        } catch (IllegalArgumentException ignored) {
            // Compatibility for cookies issued before UUID JWT subjects were
            // introduced. These cookies expire naturally within one JWT TTL.
            String userId = jwt.getClaimAsString("userId");
            if (userId == null) {
                throw invalidSession();
            }
            try {
                UserAccount user = users.findById(UUID.fromString(userId))
                        .orElseThrow(this::invalidSession);
                if (!user.getEmail().equalsIgnoreCase(jwt.getSubject())) {
                    throw invalidSession();
                }
                return user;
            } catch (IllegalArgumentException exception) {
                throw invalidSession();
            }
        }
    }

    private ResponseStatusException invalidSession() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Your session is no longer valid. Please sign in again.");
    }
}
