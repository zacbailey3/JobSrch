package com.jobsrch.resume;

import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.jobsrch.common.NotFoundException;
import com.jobsrch.resume.ResumeStorageService.StoredResume;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

@Service
public class ResumeService {

    private final ResumeRepository resumes;
    private final ResumeStorageService storage;
    private final CurrentUserService currentUsers;

    public ResumeService(
            ResumeRepository resumes,
            ResumeStorageService storage,
            CurrentUserService currentUsers) {
        this.resumes = resumes;
        this.storage = storage;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> list(Jwt jwt) {
        UserAccount user = currentUsers.requireUser(jwt);
        return resumes.findByUserIdOrderByUploadedAtDesc(user.getId()).stream()
                .map(ResumeResponse::from)
                .toList();
    }

    @Transactional
    public ResumeResponse upload(Jwt jwt, MultipartFile file) {
        UserAccount user = currentUsers.requireUser(jwt);
        StoredResume stored = storage.store(file);
        try {
            Resume resume = resumes.save(new Resume(
                    user,
                    stored.originalFilename(),
                    stored.storedFilename(),
                    stored.contentType(),
                    stored.sizeBytes()));
            return ResumeResponse.from(resume);
        } catch (RuntimeException exception) {
            storage.delete(stored.storedFilename());
            throw exception;
        }
    }

    @Transactional
    public void delete(Jwt jwt, UUID id) {
        UserAccount user = currentUsers.requireUser(jwt);
        Resume resume = resumes.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Resume not found"));
        resumes.delete(resume);
        storage.delete(resume.getStoredFilename());
    }
}
