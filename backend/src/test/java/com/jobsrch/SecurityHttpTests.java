package com.jobsrch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jobsrch.auth.PasswordResetConfirmRequest;
import com.jobsrch.auth.PasswordResetRequest;
import com.jobsrch.auth.PasswordResetResponse;
import com.jobsrch.auth.PasswordResetService;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHttpTests {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\":\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordResetService passwordResetService;

    @Test
    void anonymousDomainRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCookieIsHttpOnlyAndSameSiteStrict() throws Exception {
        String email = uniqueEmail();
        Cookie ignored = register(email, "198.51.100.10");

        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddress("198.51.100.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains("JOBSRCH_SESSION=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/");
        assertThat(response.getContentAsString()).doesNotContain("accessToken");
    }

    @Test
    void authenticatedMutationWithoutCsrfIsRejected() throws Exception {
        Cookie session = register(uniqueEmail(), "198.51.100.20");

        mockMvc.perform(post("/api/jobs")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void angularPlainCsrfCookieAndHeaderAuthorizeMutation() throws Exception {
        MockHttpServletResponse registration = registerResponse(
                uniqueEmail(), "198.51.100.21");
        Cookie session = registration.getCookie("JOBSRCH_SESSION");
        Cookie csrfCookie = registration.getCookie("XSRF-TOKEN");
        assertThat(session).isNotNull();
        assertThat(csrfCookie).isNotNull();

        mockMvc.perform(post("/api/jobs")
                        .cookie(session, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void oneUserCannotDeleteAnotherUsersJob() throws Exception {
        Cookie owner = register(uniqueEmail(), "198.51.100.30");
        Cookie attacker = register(uniqueEmail(), "198.51.100.31");

        String response = mockMvc.perform(post("/api/jobs")
                        .with(csrf())
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher matcher = ID_PATTERN.matcher(response);
        assertThat(matcher.find()).isTrue();

        mockMvc.perform(delete("/api/jobs/{id}", matcher.group(1))
                        .with(csrf())
                        .cookie(attacker))
                .andExpect(status().isNotFound());
    }

    @Test
    void passwordResetInvalidatesPreviouslyIssuedCookie() throws Exception {
        String email = uniqueEmail();
        Cookie originalSession = register(email, "198.51.100.40");
        PasswordResetResponse reset = passwordResetService.requestReset(
                new PasswordResetRequest(email));
        passwordResetService.resetPassword(new PasswordResetConfirmRequest(
                reset.developmentResetToken(),
                "replacement-password"));

        mockMvc.perform(get("/api/jobs").cookie(originalSession))
                .andExpect(status().isUnauthorized());
    }

    private Cookie register(String email, String remoteAddress) throws Exception {
        MockHttpServletResponse response = registerResponse(email, remoteAddress);
        Cookie cookie = response.getCookie("JOBSRCH_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private MockHttpServletResponse registerResponse(String email, String remoteAddress) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .with(remoteAddress(remoteAddress))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse();
    }

    private RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String uniqueEmail() {
        return "security-" + UUID.randomUUID() + "@example.com";
    }

    private String registerJson(String email) {
        return """
                {
                  "email": "%s",
                  "password": "strong-password",
                  "firstName": "Security",
                  "lastName": "Tester"
                }
                """.formatted(email);
    }

    private String loginJson(String email) {
        return """
                {"email": "%s", "password": "strong-password"}
                """.formatted(email);
    }

    private String jobJson() {
        return """
                {
                  "company": "Example",
                  "title": "Junior Software Engineer",
                  "location": "Remote",
                  "description": "Build secure Java services.",
                  "sourceUrl": "https://example.com/jobs/1",
                  "experienceMin": 0,
                  "experienceMax": 2
                }
                """;
    }
}
