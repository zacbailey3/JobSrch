package com.jobsrch.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountDeletionService deletionService;
    private final AccountSecurityService securityService;
    private final EmailChangeService emailChangeService;
    private final SessionCookieService sessionCookies;

    public AccountController(
            AccountDeletionService deletionService,
            AccountSecurityService securityService,
            EmailChangeService emailChangeService,
            SessionCookieService sessionCookies) {
        this.deletionService = deletionService;
        this.securityService = securityService;
        this.emailChangeService = emailChangeService;
        this.sessionCookies = sessionCookies;
    }

    @GetMapping
    AccountResponse get(@AuthenticationPrincipal Jwt jwt) {
        return securityService.get(jwt);
    }

    @PostMapping("/reauth/password")
    ResponseEntity<SessionResponse> reauthenticate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordReauthenticationRequest request) {
        AuthResponse auth = securityService.reauthenticate(jwt, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.authenticated(auth).toString())
                .body(SessionResponse.from(auth));
    }

    @PutMapping("/password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request) {
        securityService.changePassword(jwt, request);
        return invalidatedSession();
    }

    @PostMapping("/email-change/request")
    EmailChangeResponse requestEmailChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeRequest request) {
        return emailChangeService.request(jwt, request);
    }

    @PostMapping("/email-change/confirm")
    ResponseEntity<Void> confirmEmailChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeConfirmRequest request) {
        emailChangeService.confirm(jwt, request);
        return invalidatedSession();
    }

    @DeleteMapping
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeleteAccountRequest request) {
        deletionService.delete(jwt, request);
        return invalidatedSession();
    }

    private ResponseEntity<Void> invalidatedSession() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookies.expired().toString())
                .build();
    }
}
