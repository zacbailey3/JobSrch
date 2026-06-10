package com.jobsrch.user;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
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
        return users.findByEmailIgnoreCase(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Your session is no longer valid. Please sign in again."));
    }
}
