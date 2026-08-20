package com.jobsrch.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.AccountSecurityProperties;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;
import com.jobsrch.user.UserAccountRepository;

@Service
public class EmailChangeService {

    private static final String REQUEST_MESSAGE =
            "A confirmation link was sent to the new email address.";

    private final CurrentUserService currentUsers;
    private final UserAccountRepository users;
    private final EmailChangeTokenRepository tokens;
    private final PasswordResetTokenRepository resetTokens;
    private final RecentAuthenticationService recentAuthentication;
    private final AccountSecurityProperties properties;
    private final AccountEmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailChangeService(
            CurrentUserService currentUsers,
            UserAccountRepository users,
            EmailChangeTokenRepository tokens,
            PasswordResetTokenRepository resetTokens,
            RecentAuthenticationService recentAuthentication,
            AccountSecurityProperties properties,
            AccountEmailService emailService) {
        this.currentUsers = currentUsers;
        this.users = users;
        this.tokens = tokens;
        this.resetTokens = resetTokens;
        this.recentAuthentication = recentAuthentication;
        this.properties = properties;
        this.emailService = emailService;
    }

    @Transactional
    public EmailChangeResponse request(Jwt jwt, EmailChangeRequest request) {
        recentAuthentication.requireRecent(jwt);
        UserAccount user = currentUsers.requireUser(jwt);
        String newEmail = normalize(request.email());
        if (user.getEmail().equalsIgnoreCase(newEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a different email address");
        }
        if (users.existsByEmailIgnoreCase(newEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already uses this email");
        }

        tokens.deleteAllByUserId(user.getId());
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(properties.emailChangeTtl());
        tokens.save(new EmailChangeToken(user.getId(), newEmail, hash(rawToken), expiresAt));
        if (!properties.exposeDevelopmentEmailChangeToken()) {
            emailService.sendEmailChangeConfirmation(newEmail, rawToken, expiresAt);
        }
        return new EmailChangeResponse(
                REQUEST_MESSAGE,
                properties.exposeDevelopmentEmailChangeToken() ? rawToken : null,
                properties.exposeDevelopmentEmailChangeToken() ? expiresAt : null);
    }

    @Transactional
    public void confirm(Jwt jwt, EmailChangeConfirmRequest request) {
        recentAuthentication.requireRecent(jwt);
        UserAccount user = currentUsers.requireUser(jwt);
        Instant now = Instant.now();
        EmailChangeToken token = tokens.findByTokenHash(hash(request.token().trim()))
                .filter(candidate -> candidate.canBeUsedAt(now))
                .filter(candidate -> candidate.getUserId().equals(user.getId()))
                .orElseThrow(this::invalidToken);
        if (users.existsByEmailIgnoreCase(token.getNewEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already uses this email");
        }

        String oldEmail = user.getEmail();
        if (!properties.exposeDevelopmentEmailChangeToken()) {
            emailService.sendEmailChangedNotice(oldEmail, token.getNewEmail());
        }
        token.markUsed(now);
        user.updateVerifiedEmail(token.getNewEmail(), now);
        user.invalidateSessions();
        resetTokens.deleteAllByUserId(user.getId());
        tokens.deleteAllByUserId(user.getId());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The email change token is invalid or has expired");
    }
}
