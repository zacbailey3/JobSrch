package com.jobsrch.analysis;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/resume-analysis")
public class ResumeAnalysisController {

    private final ResumeAnalysisService analysisService;

    public ResumeAnalysisController(ResumeAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    AnalysisResponse analyze(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AnalysisRequest request) {
        return analysisService.analyze(jwt, request);
    }
}
