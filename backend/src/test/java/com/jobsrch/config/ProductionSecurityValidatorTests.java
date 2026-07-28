package com.jobsrch.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityValidatorTests {

    @Test
    void acceptsExplicitSecureProductionSettings() {
        ProductionSecurityValidator validator = validator(
                new AuthCookieProperties("JOBSRCH_SESSION", true, "Strict"),
                new PasswordResetProperties(Duration.ofMinutes(15), false),
                List.of("https://jobs.example.com"),
                true,
                "strong-database-password");

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecureCookiesAndDevelopmentDatabasePassword() {
        ProductionSecurityValidator validator = validator(
                new AuthCookieProperties("JOBSRCH_SESSION", false, "Strict"),
                new PasswordResetProperties(Duration.ofMinutes(15), false),
                List.of("https://jobs.example.com"),
                true,
                "jobsrch");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secure authentication cookies");
    }

    @Test
    void rejectsLocalhostCorsAndDisabledMalwareScanning() {
        ProductionSecurityValidator validator = validator(
                new AuthCookieProperties("JOBSRCH_SESSION", true, "Strict"),
                new PasswordResetProperties(Duration.ofMinutes(15), false),
                List.of("http://localhost:4200"),
                false,
                "strong-database-password");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    private ProductionSecurityValidator validator(
            AuthCookieProperties cookie,
            PasswordResetProperties reset,
            List<String> origins,
            boolean scanEnabled,
            String databasePassword) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", databasePassword);
        return new ProductionSecurityValidator(
                new JwtProperties(
                        "jobsrch-api",
                        "a-real-production-secret-that-is-long-enough",
                        Duration.ofHours(8)),
                cookie,
                reset,
                new EmailProperties(
                        "re_test_key",
                        "JobSrch <accounts@example.com>",
                        URI.create("https://jobs.example.com")),
                new CorsProperties(origins),
                new MalwareScanProperties(
                        scanEnabled,
                        "clamav",
                        3310,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(30)),
                environment);
    }
}
