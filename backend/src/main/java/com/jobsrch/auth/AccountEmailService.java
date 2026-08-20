package com.jobsrch.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.EmailProperties;

@Service
class AccountEmailService {

    private final RestClient client;
    private final EmailProperties properties;

    AccountEmailService(EmailProperties properties) {
        this.client = RestClient.builder().baseUrl("https://api.resend.com").build();
        this.properties = properties;
    }

    void sendEmailChangeConfirmation(String recipient, String rawToken, Instant expiresAt) {
        String baseUrl = properties.frontendBaseUrl().toString().replaceAll("/+$", "");
        String token = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        // URL fragments stay in the browser and are not sent in HTTP requests
        // or referrer headers before Angular removes the one-time credential.
        String confirmationUrl = baseUrl + "/settings#emailChangeToken=" + token;
        send(
                recipient,
                "Confirm your new JobSrch email",
                "Confirm this address for your JobSrch account using this link:\n\n"
                        + confirmationUrl + "\n\nThis link expires at " + expiresAt + ". "
                        + "If you did not request it, ignore this email.");
    }

    void sendEmailChangedNotice(String oldEmail, String newEmail) {
        send(
                oldEmail,
                "Your JobSrch email was changed",
                "The email on your JobSrch account was changed to " + newEmail + ".\n\n"
                        + "If you did not make this change, reset your password immediately.");
    }

    private void send(String recipient, String subject, String text) {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Account email is temporarily unavailable. Please try again later.");
        }
        try {
            client.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resendApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", properties.from(),
                            "to", List.of(recipient),
                            "subject", subject,
                            "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Account email is temporarily unavailable. Please try again later.",
                    exception);
        }
    }
}
