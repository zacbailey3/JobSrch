package com.jobsrch.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobsrch.common.NotFoundException;
import com.jobsrch.job.JobPosting;
import com.jobsrch.job.JobPostingRepository;
import com.jobsrch.resume.Resume;
import com.jobsrch.resume.ResumeRepository;
import com.jobsrch.resume.ResumeStorageService;
import com.jobsrch.user.CurrentUserService;
import com.jobsrch.user.UserAccount;

/**
 * Produces an explainable local comparison between one resume and one saved
 * job. The score is deterministic: no resume text leaves the application and
 * repeated analyses of the same inputs return the same result.
 */
@Service
public class ResumeAnalysisService {

    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile("\\b(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]\\d{3}[-.\\s]\\d{4}");

    private static final List<SkillRule> SKILLS = List.of(
            skill("Java", "java"),
            skill("Spring Boot", "spring boot", "spring"),
            skill("REST APIs", "rest api", "rest apis", "restful", "web api"),
            skill("SQL", "sql"),
            skill("MySQL", "mysql"),
            skill("PostgreSQL", "postgresql", "postgres"),
            skill("Angular", "angular"),
            skill("TypeScript", "typescript"),
            skill("JavaScript", "javascript"),
            skill("HTML", "html"),
            skill("CSS", "css"),
            skill("React", "react"),
            skill("Node.js", "node.js", "nodejs"),
            skill("Python", "python"),
            skill("C#", "c#"),
            skill(".NET", ".net", "dotnet"),
            skill("Git", "git"),
            skill("Maven", "maven"),
            skill("Gradle", "gradle"),
            skill("JUnit", "junit"),
            skill("Docker", "docker"),
            skill("Kubernetes", "kubernetes", "k8s"),
            skill("AWS", "aws", "amazon web services"),
            skill("Azure", "azure"),
            skill("Linux", "linux"),
            skill("Microservices", "microservices", "microservice"),
            skill("Agile", "agile"),
            skill("Scrum", "scrum"),
            skill("Communication", "communication"),
            skill("Teamwork", "teamwork", "collaboration"));

    private final ResumeRepository resumes;
    private final JobPostingRepository jobs;
    private final CurrentUserService currentUsers;
    private final ResumeStorageService storage;
    private final ResumeTextExtractor textExtractor;

    public ResumeAnalysisService(
            ResumeRepository resumes,
            JobPostingRepository jobs,
            CurrentUserService currentUsers,
            ResumeStorageService storage,
            ResumeTextExtractor textExtractor) {
        this.resumes = resumes;
        this.jobs = jobs;
        this.currentUsers = currentUsers;
        this.storage = storage;
        this.textExtractor = textExtractor;
    }

    @Transactional(readOnly = true)
    public AnalysisResponse analyze(Jwt jwt, AnalysisRequest request) {
        UserAccount user = currentUsers.requireUser(jwt);
        Resume resume = resumes.findByIdAndUserId(request.resumeId(), user.getId())
                .orElseThrow(() -> new NotFoundException("Resume not found"));
        JobPosting job = jobs.findByIdAndOwnerId(request.jobPostingId(), user.getId())
                .orElseThrow(() -> new NotFoundException("Job posting not found"));

        Path resumePath = storage.pathFor(resume.getStoredFilename());
        String resumeText = normalize(textExtractor.extract(resumePath, resume.getContentType()));
        String jobText = normalize(job.getTitle() + " " + nullToEmpty(job.getDescription()));

        List<String> requiredSkills = skillsFoundIn(jobText);
        List<String> resumeSkills = skillsFoundIn(resumeText);
        List<String> matchedSkills = requiredSkills.stream().filter(resumeSkills::contains).toList();
        List<String> missingSkills = requiredSkills.stream().filter(skill -> !resumeSkills.contains(skill)).toList();

        int keywordScore = requiredSkills.isEmpty()
                ? 50
                : percentage(matchedSkills.size(), requiredSkills.size());
        int experienceScore = experienceScore(resumeText, job.getExperienceMin());
        int structureScore = structureScore(resumeText);
        int overallScore = (int) Math.round(
                keywordScore * 0.70 + experienceScore * 0.20 + structureScore * 0.10);

        return new AnalysisResponse(
                resume.getId(),
                job.getId(),
                overallScore,
                keywordScore,
                experienceScore,
                structureScore,
                matchedSkills,
                missingSkills,
                suggestions(requiredSkills, missingSkills, resumeText, job.getExperienceMin()));
    }

    private List<String> skillsFoundIn(String text) {
        return SKILLS.stream()
                .filter(rule -> rule.aliases().stream().anyMatch(alias -> containsTerm(text, alias)))
                .map(SkillRule::name)
                .toList();
    }

    private boolean containsTerm(String text, String term) {
        if (term.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == ' ')) {
            return Pattern.compile("\\b" + Pattern.quote(term) + "\\b").matcher(text).find();
        }
        return text.contains(term);
    }

    private int experienceScore(String resumeText, Integer requiredYears) {
        if (requiredYears == null || requiredYears <= 0) {
            return 100;
        }
        int detectedYears = detectedExperienceYears(resumeText);
        if (detectedYears == 0) {
            return 40;
        }
        return Math.min(100, percentage(detectedYears, requiredYears));
    }

    private int detectedExperienceYears(String text) {
        Matcher matcher = EXPERIENCE_PATTERN.matcher(text);
        int maximum = 0;
        while (matcher.find()) {
            maximum = Math.max(maximum, Integer.parseInt(matcher.group(1)));
        }
        return maximum;
    }

    private int structureScore(String resumeText) {
        int score = 0;
        if (EMAIL_PATTERN.matcher(resumeText).find()) {
            score += 25;
        }
        if (PHONE_PATTERN.matcher(resumeText).find()) {
            score += 25;
        }
        if (containsTerm(resumeText, "education")) {
            score += 25;
        }
        if (containsTerm(resumeText, "experience") || containsTerm(resumeText, "skills")) {
            score += 25;
        }
        return score;
    }

    private List<String> suggestions(
            List<String> requiredSkills,
            List<String> missingSkills,
            String resumeText,
            Integer requiredYears) {
        List<String> suggestions = new ArrayList<>();
        if (requiredSkills.isEmpty()) {
            suggestions.add("Add a detailed job description to produce a stronger keyword comparison.");
        } else if (!missingSkills.isEmpty()) {
            suggestions.add("If accurate, show evidence of these job skills: " + String.join(", ", missingSkills) + ".");
        }
        if (!EMAIL_PATTERN.matcher(resumeText).find() || !PHONE_PATTERN.matcher(resumeText).find()) {
            suggestions.add("Include clear email and phone contact details near the top of the resume.");
        }
        if (!containsTerm(resumeText, "skills")) {
            suggestions.add("Add a concise skills section using terms that are supported by your experience.");
        }
        if (requiredYears != null && requiredYears > 0 && detectedExperienceYears(resumeText) == 0) {
            suggestions.add("Quantify internships, projects, or work experience with dates or years.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("The resume covers the main signals found in this posting; tailor achievements to the role.");
        }
        return List.copyOf(suggestions);
    }

    private int percentage(int part, int total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static SkillRule skill(String name, String... aliases) {
        return new SkillRule(name, List.of(aliases));
    }

    private record SkillRule(String name, List<String> aliases) {
    }
}
