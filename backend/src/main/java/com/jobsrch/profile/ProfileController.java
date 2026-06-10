package com.jobsrch.profile;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return profileService.get(jwt);
    }

    @PutMapping
    ProfileResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileRequest request) {
        return profileService.update(jwt, request);
    }
}
