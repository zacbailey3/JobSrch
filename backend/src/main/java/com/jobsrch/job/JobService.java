package com.jobsrch.job;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsrch.common.NotFoundException;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

@Service
public class JobService {

    private final JobPostingRepository jobs;
    private final CurrentUserService currentUsers;

    public JobService(JobPostingRepository jobs, CurrentUserService currentUsers) {
        this.jobs = jobs;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public List<JobResponse> list(org.springframework.security.oauth2.jwt.Jwt jwt, String query) {
        UserAccount user = currentUsers.requireUser(jwt);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return jobs.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(job -> normalized.isBlank()
                        || job.getCompany().toLowerCase(Locale.ROOT).contains(normalized)
                        || job.getTitle().toLowerCase(Locale.ROOT).contains(normalized))
                .map(JobResponse::from)
                .toList();
    }

    @Transactional
    public JobResponse create(org.springframework.security.oauth2.jwt.Jwt jwt, JobRequest request) {
        return JobResponse.from(jobs.save(new JobPosting(currentUsers.requireUser(jwt), request)));
    }

    @Transactional
    public JobResponse update(org.springframework.security.oauth2.jwt.Jwt jwt, UUID id, JobRequest request) {
        UserAccount user = currentUsers.requireUser(jwt);
        JobPosting job = requireOwnedJob(id, user.getId());
        job.update(request);
        return JobResponse.from(job);
    }

    @Transactional
    public void delete(org.springframework.security.oauth2.jwt.Jwt jwt, UUID id) {
        UserAccount user = currentUsers.requireUser(jwt);
        jobs.delete(requireOwnedJob(id, user.getId()));
    }

    private JobPosting requireOwnedJob(UUID id, UUID userId) {
        return jobs.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new NotFoundException("Job posting not found"));
    }
}
