package com.jobsrch.auth;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

@Service
public class AccountSecurityService {

    private final CurrentUserService currentUsers;
    private final PasswordEncoder passwordEncoder;
    private final RecentAuthenticationService recentAuthentication;
    private final AuthService authService;
    private final PasswordResetTokenRepository resetTokens;

    public AccountSecurityService(
            CurrentUserService currentUsers,
            PasswordEncoder passwordEncoder,
            RecentAuthenticationService recentAuthentication,
            AuthService authService,
            PasswordResetTokenRepository resetTokens) {
        this.currentUsers = currentUsers;
        this.passwordEncoder = passwordEncoder;
        this.recentAuthentication = recentAuthentication;
        this.authService = authService;
        this.resetTokens = resetTokens;
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Jwt jwt) {
        UserAccount user = currentUsers.requireUser(jwt);
        return AccountResponse.from(
                user,
                recentAuthentication.authenticatedAt(jwt),
                recentAuthentication.recentAuthenticationExpiresAt(jwt));
    }

    @Transactional(readOnly = true)
    public AuthResponse reauthenticate(Jwt jwt, PasswordReauthenticationRequest request) {
        UserAccount user = currentUsers.requireUser(jwt);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password is incorrect");
        }
        return authService.createResponse(user, Instant.now());
    }

    @Transactional
    public void changePassword(Jwt jwt, PasswordChangeRequest request) {
        recentAuthentication.requireRecent(jwt);
        UserAccount user = currentUsers.requireUser(jwt);
        user.updatePasswordHash(passwordEncoder.encode(request.password()));
        user.invalidateSessions();
        resetTokens.deleteAllByUserId(user.getId());
    }
}
