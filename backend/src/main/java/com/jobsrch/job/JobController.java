package com.jobsrch.job;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    List<JobResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String query) {
        return jobService.list(jwt, query);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    JobResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JobRequest request) {
        return jobService.create(jwt, request);
    }

    @PutMapping("/{id}")
    JobResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody JobRequest request) {
        return jobService.update(jwt, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        jobService.delete(jwt, id);
    }
}
