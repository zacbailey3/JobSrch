package com.jobsrch.dashboard;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobsrch.application.ApplicationStatus;
import com.jobsrch.application.JobApplicationRepository;
import com.jobsrch.job.JobPostingRepository;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CurrentUserService currentUsers;
    private final JobPostingRepository jobs;
    private final JobApplicationRepository applications;

    public DashboardController(
            CurrentUserService currentUsers,
            JobPostingRepository jobs,
            JobApplicationRepository applications) {
        this.currentUsers = currentUsers;
        this.jobs = jobs;
        this.applications = applications;
    }

    @GetMapping
    @Transactional(readOnly = true)
    DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        UserAccount user = currentUsers.requireUser(jwt);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Arrays.stream(ApplicationStatus.values()).forEach(status ->
                byStatus.put(status.name(), applications.countByUserIdAndStatus(user.getId(), status)));
        return new DashboardResponse(
                jobs.countByOwnerId(user.getId()),
                applications.countByUserId(user.getId()),
                byStatus);
    }
}
