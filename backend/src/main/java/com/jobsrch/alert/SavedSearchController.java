package com.jobsrch.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {

    private final SavedSearchAlertService service;

    public SavedSearchController(SavedSearchAlertService service) {
        this.service = service;
    }

    @GetMapping
    List<SavedSearchResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(jwt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavedSearchResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SavedSearchRequest request) {
        return service.create(jwt, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(jwt, id);
    }

    @GetMapping("/alerts")
    List<SearchAlertResponse> alerts(@AuthenticationPrincipal Jwt jwt) {
        return service.listAlerts(jwt);
    }

    @PostMapping("/alerts/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAlertsSeen(@AuthenticationPrincipal Jwt jwt) {
        service.markAllSeen(jwt);
    }
}
