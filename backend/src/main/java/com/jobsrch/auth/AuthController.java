package com.jobsrch.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import com.jobsrch.user.CurrentUserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SessionCookieService sessionCookies;
    private final CurrentUserService currentUsers;
    private final RecentAuthenticationService recentAuthentication;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            SessionCookieService sessionCookies,
            CurrentUserService currentUsers,
            RecentAuthenticationService recentAuthentication) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.sessionCookies = sessionCookies;
        this.currentUsers = currentUsers;
        this.recentAuthentication = recentAuthentication;
    }

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
        long expiresIn = Math.max(0, Duration.between(Instant.now(), jwt.getExpiresAt()).toSeconds());
        return SessionResponse.from(
                currentUsers.requireUser(jwt),
                expiresIn,
                recentAuthentication.authenticatedAt(jwt));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<SessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authenticated(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticated(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.expired().toString())
                .build();
    }

    @PostMapping("/password-reset/request")
    PasswordResetResponse requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        return passwordResetService.requestReset(request);
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request);
    }

    private ResponseEntity<SessionResponse> authenticated(AuthResponse auth, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, sessionCookies.authenticated(auth).toString())
                .body(SessionResponse.from(auth));
    }
}
