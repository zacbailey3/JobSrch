package com.jobsrch.profile;

import java.time.Instant;
import java.util.UUID;

import com.jobsrch.user.UserAccount;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "career_profiles")
public class CareerProfile {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    private String phone;
    private String location;
    private String headline;
    private String education;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "desired_roles", length = 1000)
    private String desiredRoles;

    @Column(length = 2000)
    private String skills;

    @Column(name = "linkedin_url", length = 1000)
    private String linkedinUrl;

    @Column(name = "portfolio_url", length = 1000)
    private String portfolioUrl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerProfile() {
    }

    public CareerProfile(UserAccount user) {
        this.user = user;
        this.updatedAt = Instant.now();
    }

    public void update(ProfileRequest request) {
        this.phone = request.phone();
        this.location = request.location();
        this.headline = request.headline();
        this.education = request.education();
        this.graduationYear = request.graduationYear();
        this.yearsExperience = request.yearsExperience();
        this.desiredRoles = request.desiredRoles();
        this.skills = request.skills();
        this.linkedinUrl = request.linkedinUrl();
        this.portfolioUrl = request.portfolioUrl();
        this.updatedAt = Instant.now();
    }

    public String getPhone() {
        return phone;
    }

    public String getLocation() {
        return location;
    }

    public String getHeadline() {
        return headline;
    }

    public String getEducation() {
        return education;
    }

    public Integer getGraduationYear() {
        return graduationYear;
    }

    public Integer getYearsExperience() {
        return yearsExperience;
    }

    public String getDesiredRoles() {
        return desiredRoles;
    }

    public String getSkills() {
        return skills;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getPortfolioUrl() {
        return portfolioUrl;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
