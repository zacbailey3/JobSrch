package com.jobsrch.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.PasswordResetProperties;
import com.jobsrch.user.UserAccount;
import com.jobsrch.user.UserAccountRepository;

@Service
public class PasswordResetService {

    private static final String GENERIC_MESSAGE =
            "If an account uses that email, password reset instructions are available.";

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserAccountRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            PasswordResetProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public PasswordResetResponse requestReset(PasswordResetRequest request) {
        return users.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .map(this::createResetToken)
                .orElseGet(() -> new PasswordResetResponse(GENERIC_MESSAGE, null, null));
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request) {
        Instant now = Instant.now();
        PasswordResetToken token = tokens.findByTokenHash(hash(request.token().trim()))
                .filter(candidate -> candidate.canBeUsedAt(now))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "The reset token is invalid or has expired"));

        UserAccount user = users.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "The reset token is invalid or has expired"));
        user.updatePasswordHash(passwordEncoder.encode(request.password()));
        token.markUsed(now);
        tokens.deleteAllByUserId(user.getId());
    }

    private PasswordResetResponse createResetToken(UserAccount user) {
        tokens.deleteAllByUserId(user.getId());
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(properties.ttl());
        tokens.save(new PasswordResetToken(user.getId(), hash(rawToken), expiresAt));
        return new PasswordResetResponse(
                GENERIC_MESSAGE,
                properties.exposeDevelopmentToken() ? rawToken : null,
                properties.exposeDevelopmentToken() ? expiresAt : null);
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
}
