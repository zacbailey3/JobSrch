package com.jobsrch.application;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsrch.common.NotFoundException;
import com.jobsrch.job.JobPosting;
import com.jobsrch.job.JobPostingRepository;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

/**
 * Coordinates user-owned application records.
 *
 * <p>Every lookup combines the requested id with the authenticated user's id.
 * This prevents an otherwise valid JWT from reading or mutating another user's
 * application by guessing its UUID.</p>
 */
@Service
public class ApplicationService {

    private final JobApplicationRepository applications;
    private final JobPostingRepository jobs;
    private final CurrentUserService currentUsers;

    public ApplicationService(
            JobApplicationRepository applications,
            JobPostingRepository jobs,
            CurrentUserService currentUsers) {
        this.applications = applications;
        this.jobs = jobs;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(Jwt jwt) {
        UserAccount user = currentUsers.requireUser(jwt);
        return applications.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @Transactional
    public ApplicationResponse create(Jwt jwt, ApplicationRequest request) {
        UserAccount user = currentUsers.requireUser(jwt);
        JobPosting job = findOptionalOwnedJob(request.jobPostingId(), user.getId());
        return ApplicationResponse.from(
                applications.save(new JobApplication(user, job, request)));
    }

    @Transactional
    public ApplicationResponse update(Jwt jwt, UUID id, ApplicationRequest request) {
        UserAccount user = currentUsers.requireUser(jwt);
        JobApplication application = requireOwnedApplication(id, user.getId());
        JobPosting job = findOptionalOwnedJob(request.jobPostingId(), user.getId());
        application.update(job, request);
        return ApplicationResponse.from(application);
    }

    @Transactional
    public void delete(Jwt jwt, UUID id) {
        UserAccount user = currentUsers.requireUser(jwt);
        applications.delete(requireOwnedApplication(id, user.getId()));
    }

    private JobPosting findOptionalOwnedJob(UUID jobId, UUID userId) {
        if (jobId == null) {
            return null;
        }
        return jobs.findByIdAndOwnerId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Linked job posting not found"));
    }

    private JobApplication requireOwnedApplication(UUID id, UUID userId) {
        return applications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Application not found"));
    }
}
