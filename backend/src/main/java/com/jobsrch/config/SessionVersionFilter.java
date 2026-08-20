package com.jobsrch.config;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jobsrch.user.UserAccount;
import com.jobsrch.user.UserAccountRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rejects cryptographically valid JWTs that predate a password reset.
 */
public class SessionVersionFilter extends OncePerRequestFilter {

    private final UserAccountRepository users;

    public SessionVersionFilter(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && !isCurrent(jwtAuthentication)) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"title\":\"Unauthorized\",\"status\":401,"
                            + "\"detail\":\"Your session is no longer valid. Please sign in again.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isCurrent(JwtAuthenticationToken authentication) {
        try {
            Object claim = authentication.getToken().getClaim("securityVersion");
            if (!(claim instanceof Number version)) {
                return false;
            }
            UserAccount user = resolveUser(authentication);
            return user != null
                    && user.getSecurityVersion() == version.longValue();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private UserAccount resolveUser(JwtAuthenticationToken authentication) {
        String subject = authentication.getToken().getSubject();
        try {
            return users.findById(UUID.fromString(subject)).orElse(null);
        } catch (IllegalArgumentException ignored) {
            String userId = authentication.getToken().getClaimAsString("userId");
            if (userId == null) {
                return null;
            }
            UserAccount legacyUser = users.findById(UUID.fromString(userId)).orElse(null);
            return legacyUser != null && legacyUser.getEmail().equalsIgnoreCase(subject)
                    ? legacyUser
                    : null;
        }
    }
}
