package com.jobsrch.config;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Stops a production deployment instead of silently accepting development
 * credentials or browser security settings.
 */
@Component
@Profile("prod")
public class ProductionSecurityValidator implements InitializingBean {

    private static final String DEVELOPMENT_JWT_SECRET =
            "change-this-development-secret-to-at-least-32-characters";

    private final JwtProperties jwt;
    private final AuthCookieProperties cookie;
    private final PasswordResetProperties passwordReset;
    private final AccountSecurityProperties accountSecurity;
    private final EmailProperties email;
    private final CorsProperties cors;
    private final MalwareScanProperties malwareScan;
    private final Environment environment;

    public ProductionSecurityValidator(
            JwtProperties jwt,
            AuthCookieProperties cookie,
            PasswordResetProperties passwordReset,
            AccountSecurityProperties accountSecurity,
            EmailProperties email,
            CorsProperties cors,
            MalwareScanProperties malwareScan,
            Environment environment) {
        this.jwt = jwt;
        this.cookie = cookie;
        this.passwordReset = passwordReset;
        this.accountSecurity = accountSecurity;
        this.email = email;
        this.cors = cors;
        this.malwareScan = malwareScan;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        require(hasText(jwt.secret()) && !DEVELOPMENT_JWT_SECRET.equals(jwt.secret()),
                "Production requires a non-default JWT_SECRET");
        require(cookie.secure(), "Production requires secure authentication cookies");
        require(!passwordReset.exposeDevelopmentToken(),
                "Production must not expose password reset tokens");
        require(!accountSecurity.exposeDevelopmentEmailChangeToken(),
                "Production must not expose email change tokens");
        require(hasText(environment.getProperty("spring.datasource.password"))
                        && !"jobsrch".equals(environment.getProperty("spring.datasource.password")),
                "Production requires a non-default DB_PASSWORD");
        require(hasText(email.resendApiKey()), "Production requires RESEND_API_KEY");
        require(hasText(email.from()), "Production requires PASSWORD_RESET_FROM");
        require(isSecurePublicUrl(email.frontendBaseUrl()),
                "FRONTEND_BASE_URL must be a public HTTPS URL in production");
        require(validOrigins(cors.allowedOrigins()),
                "CORS_ALLOWED_ORIGINS must contain only explicit public HTTPS origins");
        require(malwareScan.enabled(),
                "Production requires malware scanning for resume uploads");
    }

    private boolean validOrigins(List<String> origins) {
        return origins != null
                && !origins.isEmpty()
                && origins.stream().allMatch(this::isSecurePublicUrl);
    }

    private boolean isSecurePublicUrl(String value) {
        try {
            return isSecurePublicUrl(URI.create(value));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isSecurePublicUrl(URI uri) {
        if (uri == null) {
            return false;
        }
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && !host.equalsIgnoreCase("localhost")
                && !host.equals("127.0.0.1")
                && !host.equals("0.0.0.0")
                && !uri.toString().contains("*");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
