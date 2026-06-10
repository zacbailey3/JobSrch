package com.jobsrch.profile;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

@Service
public class ProfileService {

    private final CareerProfileRepository profiles;
    private final CurrentUserService currentUsers;

    public ProfileService(CareerProfileRepository profiles, CurrentUserService currentUsers) {
        this.profiles = profiles;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Jwt jwt) {
        UserAccount user = currentUsers.requireUser(jwt);
        return profiles.findById(user.getId())
                .map(ProfileResponse::from)
                .orElseGet(ProfileResponse::empty);
    }

    @Transactional
    public ProfileResponse update(Jwt jwt, ProfileRequest request) {
        UserAccount user = currentUsers.requireUser(jwt);
        CareerProfile profile = profiles.findById(user.getId())
                .orElseGet(() -> new CareerProfile(user));
        profile.update(request);
        return ProfileResponse.from(profiles.save(profile));
    }
}
