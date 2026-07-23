package com.jobsrch.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.jobsrch.config.EmailProperties;

/**
 * Sends reset links through Resend's HTTPS API. This provider boundary can be
 * replaced later without changing token creation or validation.
 */
@Service
public class PasswordResetEmailService {

    private final RestClient client;
    private final EmailProperties properties;

    public PasswordResetEmailService(EmailProperties properties) {
        this.client = RestClient.builder().baseUrl("https://api.resend.com").build();
        this.properties = properties;
    }

    public void send(String recipient, String rawToken, Instant expiresAt) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "Password reset email is not configured. Set RESEND_API_KEY, "
                            + "PASSWORD_RESET_FROM, and FRONTEND_BASE_URL.");
        }

        String token = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String separator = properties.frontendBaseUrl().toString().contains("?") ? "&" : "?";
        String resetUrl = properties.frontendBaseUrl() + separator + "resetToken=" + token;
        String text = "Reset your JobSrch password using this link:\n\n"
                + resetUrl + "\n\nThis link expires at " + expiresAt + ". "
                + "If you did not request it, ignore this email.";

        client.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resendApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "from", properties.from(),
                        "to", List.of(recipient),
                        "subject", "Reset your JobSrch password",
                        "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
