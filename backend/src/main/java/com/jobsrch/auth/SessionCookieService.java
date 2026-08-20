package com.jobsrch.auth;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.jobsrch.config.AuthCookieProperties;

@Service
class SessionCookieService {

    private final AuthCookieProperties properties;

    SessionCookieService(AuthCookieProperties properties) {
        this.properties = properties;
    }

    ResponseCookie authenticated(AuthResponse response) {
        return cookie(response.accessToken(), response.expiresIn());
    }

    ResponseCookie expired() {
        return cookie("", 0);
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
