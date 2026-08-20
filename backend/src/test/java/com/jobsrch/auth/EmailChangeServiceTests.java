package com.jobsrch.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

import com.jobsrch.user.UserAccount;
import com.jobsrch.user.UserAccountRepository;

@SpringBootTest
@Transactional
class EmailChangeServiceTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private EmailChangeTokenRepository tokens;

    @Autowired
    private EmailChangeService emailChangeService;

    @Test
    void expiredEmailChangeTokenFailsWithoutChangingTheAccount() throws Exception {
        AuthResponse auth = authService.register(new RegisterRequest(
                "expired-email-change@example.com",
                "strong-password",
                "Expired",
                "Token"));
        Jwt jwt = jwtDecoder.decode(auth.accessToken());
        UserAccount user = users.findById(auth.userId()).orElseThrow();
        tokens.save(new EmailChangeToken(
                user.getId(),
                "replacement@example.com",
                hash("expired-token"),
                Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> emailChangeService.confirm(
                jwt,
                new EmailChangeConfirmRequest("expired-token")))
                .hasMessageContaining("invalid or has expired");
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
