package com.jobsrch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountSecurityHttpTests {

    private static final Pattern DEVELOPMENT_TOKEN =
            Pattern.compile("\"developmentToken\":\"([^\"]+)\"");
    private static final AtomicInteger ADDRESS_SEQUENCE = new AtomicInteger(50);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void newSessionsUseImmutableUuidSubjectsAndIncludeAuthenticationTime() throws Exception {
        Cookie session = register(uniqueEmail());
        Jwt jwt = jwtDecoder.decode(session.getValue());

        assertThatCodeIsUuid(jwt.getSubject());
        assertThat((Object) jwt.getClaim("userId")).isNull();
        assertThat(((Number) jwt.getClaim("authTime")).longValue()).isPositive();

        mockMvc.perform(get("/api/account").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.authenticatedAt").isNotEmpty())
                .andExpect(jsonPath("$.recentAuthenticationExpiresAt").isNotEmpty());
    }

    @Test
    void legacyEmailSubjectCookieRemainsUsableButRequiresReauthenticationForSensitiveWork()
            throws Exception {
        String email = uniqueEmail();
        Cookie current = register(email);
        Jwt currentJwt = jwtDecoder.decode(current.getValue());
        Instant now = Instant.now();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwtClaimsSet.builder()
                        .issuer("jobsrch-test")
                        .subject(email)
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(600))
                        .claim("userId", currentJwt.getSubject())
                        .claim("securityVersion", 0)
                        .build()))
                .getTokenValue();
        Cookie legacy = new Cookie("JOBSRCH_SESSION", token);

        mockMvc.perform(get("/api/auth/session").cookie(legacy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.authenticatedAt").doesNotExist());

        mockMvc.perform(delete("/api/account")
                        .with(csrf())
                        .cookie(legacy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticationOlderThanTenMinutesCannotAuthorizeSensitiveWork() throws Exception {
        Cookie current = register(uniqueEmail());
        Jwt currentJwt = jwtDecoder.decode(current.getValue());
        Instant now = Instant.now();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwtClaimsSet.builder()
                        .issuer("jobsrch-test")
                        .subject(currentJwt.getSubject())
                        .issuedAt(now.minusSeconds(700))
                        .expiresAt(now.plusSeconds(600))
                        .claim("securityVersion", 0)
                        .claim("authTime", now.minusSeconds(601).getEpochSecond())
                        .build()))
                .getTokenValue();

        mockMvc.perform(delete("/api/account")
                        .with(csrf())
                        .cookie(new Cookie("JOBSRCH_SESSION", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordReauthenticationIssuesAFreshSessionAndRejectsWrongPassword() throws Exception {
        Cookie session = register(uniqueEmail());

        mockMvc.perform(post("/api/account/reauth/password")
                        .with(csrf())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpServletResponse response = mockMvc.perform(post("/api/account/reauth/password")
                        .with(csrf())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"strong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticatedAt").isNotEmpty())
                .andReturn()
                .getResponse();

        assertThat(response.getCookie("JOBSRCH_SESSION")).isNotNull();
    }

    @Test
    void passwordChangeInvalidatesExistingSessions() throws Exception {
        String email = uniqueEmail();
        Cookie session = register(email);

        mockMvc.perform(put("/api/account/password")
                        .with(csrf())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"replacement-password\"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                        .contains("JOBSRCH_SESSION=").contains("Max-Age=0"));

        mockMvc.perform(get("/api/auth/session").cookie(session))
                .andExpect(status().isUnauthorized());
        login(email, "strong-password", status().isUnauthorized());
        login(email, "replacement-password", status().isOk());
    }

    @Test
    void emailChangeRejectsCollisionsAndCrossAccountTokensThenInvalidatesSessions()
            throws Exception {
        String originalEmail = uniqueEmail();
        String newEmail = uniqueEmail();
        String otherEmail = uniqueEmail();
        Cookie owner = register(originalEmail);
        Cookie other = register(otherEmail);

        mockMvc.perform(post("/api/account/email-change/request")
                        .with(csrf())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + otherEmail + "\"}"))
                .andExpect(status().isConflict());

        String response = mockMvc.perform(post("/api/account/email-change/request")
                        .with(csrf())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + newEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.developmentToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = developmentToken(response);

        confirmEmail(other, token, status().isBadRequest());
        confirmEmail(owner, "not-the-token", status().isBadRequest());
        confirmEmail(owner, token, status().isNoContent());

        mockMvc.perform(get("/api/auth/session").cookie(owner))
                .andExpect(status().isUnauthorized());
        login(originalEmail, "strong-password", status().isUnauthorized());
        Cookie changedSession = login(newEmail, "strong-password", status().isOk());
        mockMvc.perform(get("/api/account").cookie(changedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.emailVerified").value(true));
        confirmEmail(changedSession, token, status().isBadRequest());
    }

    @Test
    void deletionRequiresExactConfirmationAndInvalidatesTheCookie() throws Exception {
        String email = uniqueEmail();
        Cookie session = register(email);

        mockMvc.perform(delete("/api/account")
                        .with(csrf())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"delete\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/account")
                        .with(csrf())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                        .contains("JOBSRCH_SESSION=").contains("Max-Age=0"));

        login(email, "strong-password", status().isUnauthorized());
    }

    private Cookie confirmEmail(Cookie session, String token, org.springframework.test.web.servlet.ResultMatcher status)
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/account/email-change/confirm")
                        .with(csrf())
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status)
                .andReturn()
                .getResponse();
        return response.getCookie("JOBSRCH_SESSION");
    }

    private Cookie register(String email) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/register")
                        .with(remoteAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "strong-password",
                                  "firstName": "Account",
                                  "lastName": "Tester"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
        Cookie session = response.getCookie("JOBSRCH_SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie login(
            String email,
            String password,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(expectedStatus)
                .andReturn()
                .getResponse();
        return response.getCookie("JOBSRCH_SESSION");
    }

    private RequestPostProcessor remoteAddress() {
        int suffix = ADDRESS_SEQUENCE.getAndIncrement();
        return request -> {
            request.setRemoteAddr("198.51.100." + suffix);
            return request;
        };
    }

    private String developmentToken(String response) {
        Matcher matcher = DEVELOPMENT_TOKEN.matcher(response);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String uniqueEmail() {
        return "account-" + UUID.randomUUID() + "@example.com";
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
