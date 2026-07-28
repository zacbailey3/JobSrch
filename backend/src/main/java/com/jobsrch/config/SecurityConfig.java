package com.jobsrch.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.jobsrch.user.UserAccountRepository;

/**
 * Configures the API as a stateless JWT resource server.
 *
 * <p>Only account establishment/recovery and health checks are public. Every
 * domain service still verifies record ownership because authentication alone
 * does not prove that a user owns a requested job, application, profile, or
 * resume.</p>
 */
@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        PasswordResetProperties.class,
        CorsProperties.class,
        StorageProperties.class,
        UsaJobsProperties.class,
        AdzunaProperties.class,
        AuthCookieProperties.class,
        EmailProperties.class,
        MalwareScanProperties.class,
        ResumeAnalysisProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthCookieProperties cookieProperties,
            UserAccountRepository users,
            JwtDecoder jwtDecoder) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(jwtDecoder);
        BearerTokenAuthenticationConverter authenticationConverter =
                new BearerTokenAuthenticationConverter();
        authenticationConverter.setBearerTokenResolver(
                new CookieBearerTokenResolver(cookieProperties.name()));
        BearerTokenAuthenticationFilter bearerFilter =
                new BearerTokenAuthenticationFilter(
                        new ProviderManager(jwtProvider),
                        authenticationConverter);
        bearerFilter.setAuthenticationEntryPoint(new BearerTokenAuthenticationEntryPoint());
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // These endpoints establish or recover authentication and
                        // therefore cannot require a pre-existing CSRF cookie.
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm"))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                // Spring's resource-server DSL assumes bearer tokens are never
                // cookies and therefore excludes them from CSRF. The explicit
                // filter keeps JWT authentication while preserving cookie CSRF.
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SessionVersionFilter(users), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new AuthRateLimitFilter(), SessionVersionFilter.class)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/password-reset/request",
                                "/api/auth/password-reset/confirm",
                                "/actuator/health")
                        .permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        properties.allowedOrigins().forEach(configuration::addAllowedOrigin);
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private SecretKey secretKey(JwtProperties properties) {
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
