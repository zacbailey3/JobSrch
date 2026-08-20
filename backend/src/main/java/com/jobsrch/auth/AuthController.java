package com.jobsrch.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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
import com.jobsrch.config.AuthCookieProperties;
import com.jobsrch.user.CurrentUserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthCookieProperties cookieProperties;
    private final CurrentUserService currentUsers;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            AuthCookieProperties cookieProperties,
            CurrentUserService currentUsers) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.cookieProperties = cookieProperties;
        this.currentUsers = currentUsers;
    }

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
        long expiresIn = Math.max(0, Duration.between(Instant.now(), jwt.getExpiresAt()).toSeconds());
        return SessionResponse.from(currentUsers.requireUser(jwt), expiresIn);
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
        ResponseCookie expired = cookie("", 0);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
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
        ResponseCookie sessionCookie = cookie(auth.accessToken(), auth.expiresIn());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
                .body(SessionResponse.from(auth));
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(cookieProperties.name(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
