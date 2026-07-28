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
            String userId = authentication.getToken().getClaimAsString("userId");
            Object claim = authentication.getToken().getClaim("securityVersion");
            if (userId == null || !(claim instanceof Number version)) {
                return false;
            }
            UserAccount user = users.findById(UUID.fromString(userId)).orElse(null);
            return user != null
                    && user.getEmail().equalsIgnoreCase(authentication.getToken().getSubject())
                    && user.getSecurityVersion() == version.longValue();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
