package com.jobsrch.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.alert.SavedSearchRepository;
import com.jobsrch.application.JobApplicationRepository;
import com.jobsrch.job.JobPostingRepository;
import com.jobsrch.profile.CareerProfileRepository;
import com.jobsrch.resume.ResumeRepository;
import com.jobsrch.resume.ResumeStorageService;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;
import com.jobsrch.user.UserAccountRepository;

/**
 * Permanently removes a user-owned account graph and private resume files.
 * Recent reauthentication and explicit confirmation limit damage from an
 * unattended authenticated device.
 */
@Service
public class AccountDeletionService {

    private final CurrentUserService currentUsers;
    private final RecentAuthenticationService recentAuthentication;
    private final JobApplicationRepository applications;
    private final JobPostingRepository jobs;
    private final SavedSearchRepository savedSearches;
    private final ResumeRepository resumes;
    private final ResumeStorageService resumeStorage;
    private final CareerProfileRepository profiles;
    private final PasswordResetTokenRepository resetTokens;
    private final EmailChangeTokenRepository emailChangeTokens;
    private final UserAccountRepository users;

    public AccountDeletionService(
            CurrentUserService currentUsers,
            RecentAuthenticationService recentAuthentication,
            JobApplicationRepository applications,
            JobPostingRepository jobs,
            SavedSearchRepository savedSearches,
            ResumeRepository resumes,
            ResumeStorageService resumeStorage,
            CareerProfileRepository profiles,
            PasswordResetTokenRepository resetTokens,
            EmailChangeTokenRepository emailChangeTokens,
            UserAccountRepository users) {
        this.currentUsers = currentUsers;
        this.recentAuthentication = recentAuthentication;
        this.applications = applications;
        this.jobs = jobs;
        this.savedSearches = savedSearches;
        this.resumes = resumes;
        this.resumeStorage = resumeStorage;
        this.profiles = profiles;
        this.resetTokens = resetTokens;
        this.emailChangeTokens = emailChangeTokens;
        this.users = users;
    }

    @Transactional
    public void delete(Jwt jwt, DeleteAccountRequest request) {
        recentAuthentication.requireRecent(jwt);
        if (!"DELETE".equals(request.confirmation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type DELETE exactly to confirm");
        }
        UserAccount user = currentUsers.requireUser(jwt);

        resumes.findByUserIdOrderByUploadedAtDesc(user.getId())
                .forEach(resume -> resumeStorage.delete(resume.getStoredFilename()));
        applications.deleteAllByUserId(user.getId());
        jobs.deleteAllByOwnerId(user.getId());
        savedSearches.deleteAllByUser_Id(user.getId());
        resumes.deleteAllByUserId(user.getId());
        profiles.deleteById(user.getId());
        resetTokens.deleteAllByUserId(user.getId());
        emailChangeTokens.deleteAllByUserId(user.getId());
        users.delete(user);
    }
}
