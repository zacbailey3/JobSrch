package com.jobsrch.discovery;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Extracts explainable early-career signals from provider job text.
 *
 * <p>The rules intentionally prefer "not stated" over guessing. They support
 * filtering and candidate guidance, but do not claim to replace the employer's
 * full posting.</p>
 */
@Component
public class JobInsightClassifier {

    private static final Pattern INTERNSHIP = Pattern.compile(
            "\\b(?:intern|internship|co-op|coop)\\b");
    private static final Pattern APPRENTICESHIP = Pattern.compile(
            "\\b(?:apprentice|apprenticeship)\\b");
    private static final Pattern NEW_GRAD = Pattern.compile(
            "\\b(?:new grad(?:uate)?|recent grad(?:uate)?|graduate program|campus hire)\\b");
    private static final Pattern ENTRY_LEVEL = Pattern.compile(
            "\\b(?:entry[ -]level|junior|jr\\.?|associate)\\b");
    private static final Pattern PART_TIME = Pattern.compile("\\bpart[ -]time\\b");
    private static final Pattern CONTRACT = Pattern.compile(
            "\\b(?:contract|contractor|temporary|temp role)\\b");
    private static final Pattern FULL_TIME = Pattern.compile("\\bfull[ -]time\\b");
    private static final Pattern NO_DEGREE = Pattern.compile(
            "\\b(?:no (?:college )?degree required|degree not required"
                    + "|bachelor'?s degree or equivalent experience"
                    + "|degree or equivalent practical experience)\\b");
    private static final Pattern ADVANCED_DEGREE_REQUIRED = Pattern.compile(
            "\\b(?:master'?s|ph\\.?d\\.?|doctorate|advanced degree)\\b.{0,35}"
                    + "\\b(?:required|must|minimum)\\b"
                    + "|\\b(?:required|must|minimum)\\b.{0,35}"
                    + "\\b(?:master'?s|ph\\.?d\\.?|doctorate|advanced degree)\\b");
    private static final Pattern BACHELORS_REQUIRED = Pattern.compile(
            "\\b(?:bachelor'?s|undergraduate degree|college degree)\\b.{0,35}"
                    + "\\b(?:required|must|minimum)\\b"
                    + "|\\b(?:required|must|minimum)\\b.{0,35}"
                    + "\\b(?:bachelor'?s|undergraduate degree|college degree)\\b");
    private static final Pattern DEGREE_PREFERRED = Pattern.compile(
            "\\b(?:bachelor'?s|master'?s|college degree|degree)\\b.{0,35}"
                    + "\\bpreferred\\b"
                    + "|\\bpreferred\\b.{0,35}"
                    + "\\b(?:bachelor'?s|master'?s|college degree|degree)\\b");
    private static final Pattern NO_SPONSORSHIP = Pattern.compile(
            "\\b(?:will not (?:provide|offer) (?:visa )?sponsorship"
                    + "|no (?:visa )?sponsorship"
                    + "|not (?:eligible|available) for (?:visa )?sponsorship"
                    + "|without (?:current or future )?(?:visa )?sponsorship"
                    + "|unable to sponsor)\\b");
    private static final Pattern SPONSORSHIP_AVAILABLE = Pattern.compile(
            "\\b(?:(?:visa )?sponsorship (?:is )?available"
                    + "|will (?:provide|offer) (?:visa )?sponsorship"
                    + "|can sponsor|sponsor(?:s|ing)? (?:work )?visas?)\\b");

    public DiscoveredJob enrich(DiscoveredJob job) {
        String title = normalize(job.title());
        String text = normalize(job.title() + " " + nullToEmpty(job.description()));
        OpportunityType opportunityType = opportunityType(title, text);
        CareerStage careerStage = careerStage(title, text, job.entryLevelLikely());
        DegreeRequirement degreeRequirement = degreeRequirement(text);
        SponsorshipStatus sponsorshipStatus = sponsorshipStatus(text);
        List<String> reasons = reasons(
                job, opportunityType, careerStage, degreeRequirement, sponsorshipStatus);
        List<String> cautions = cautions(job, degreeRequirement, sponsorshipStatus);

        return new DiscoveredJob(
                job.externalId(),
                job.provider(),
                job.company(),
                job.title(),
                job.location(),
                job.countryCode(),
                job.workplaceType(),
                job.description(),
                job.sourceUrl(),
                job.publishedAt(),
                job.expiresAt(),
                job.experienceMin(),
                job.experienceMax(),
                job.entryLevelLikely(),
                opportunityType,
                careerStage,
                degreeRequirement,
                sponsorshipStatus,
                job.verifiedAt() == null ? Instant.now() : job.verifiedAt(),
                reasons,
                cautions);
    }

    private OpportunityType opportunityType(String title, String text) {
        if (INTERNSHIP.matcher(title).find()) {
            return OpportunityType.INTERNSHIP;
        }
        if (APPRENTICESHIP.matcher(title).find()) {
            return OpportunityType.APPRENTICESHIP;
        }
        if (PART_TIME.matcher(text).find()) {
            return OpportunityType.PART_TIME;
        }
        if (CONTRACT.matcher(text).find()) {
            return OpportunityType.CONTRACT;
        }
        if (FULL_TIME.matcher(text).find()) {
            return OpportunityType.FULL_TIME;
        }
        return OpportunityType.UNKNOWN;
    }

    private CareerStage careerStage(String title, String text, boolean entryLevelLikely) {
        if (INTERNSHIP.matcher(title).find()) {
            return CareerStage.INTERNSHIP;
        }
        if (APPRENTICESHIP.matcher(title).find()) {
            return CareerStage.APPRENTICESHIP;
        }
        if (NEW_GRAD.matcher(text).find()) {
            return CareerStage.NEW_GRAD;
        }
        if (ENTRY_LEVEL.matcher(title).find()) {
            return CareerStage.ENTRY_LEVEL;
        }
        return entryLevelLikely ? CareerStage.EARLY_CAREER : CareerStage.UNSPECIFIED;
    }

    private DegreeRequirement degreeRequirement(String text) {
        if (NO_DEGREE.matcher(text).find()) {
            return DegreeRequirement.NO_DEGREE_REQUIRED;
        }
        if (ADVANCED_DEGREE_REQUIRED.matcher(text).find()) {
            return DegreeRequirement.ADVANCED_DEGREE_REQUIRED;
        }
        if (BACHELORS_REQUIRED.matcher(text).find()) {
            return DegreeRequirement.BACHELORS_REQUIRED;
        }
        if (DEGREE_PREFERRED.matcher(text).find()) {
            return DegreeRequirement.DEGREE_PREFERRED;
        }
        return DegreeRequirement.NOT_STATED;
    }

    private SponsorshipStatus sponsorshipStatus(String text) {
        if (NO_SPONSORSHIP.matcher(text).find()) {
            return SponsorshipStatus.NOT_AVAILABLE;
        }
        if (SPONSORSHIP_AVAILABLE.matcher(text).find()) {
            return SponsorshipStatus.AVAILABLE;
        }
        return SponsorshipStatus.NOT_STATED;
    }

    private List<String> reasons(
            DiscoveredJob job,
            OpportunityType opportunityType,
            CareerStage careerStage,
            DegreeRequirement degreeRequirement,
            SponsorshipStatus sponsorshipStatus) {
        List<String> reasons = new ArrayList<>();
        switch (careerStage) {
            case INTERNSHIP -> reasons.add("The title identifies this as an internship.");
            case APPRENTICESHIP -> reasons.add("The title identifies this as an apprenticeship.");
            case NEW_GRAD -> reasons.add("The posting explicitly mentions new or recent graduates.");
            case ENTRY_LEVEL -> reasons.add("The title explicitly uses a junior or entry-level label.");
            case EARLY_CAREER -> reasons.add(
                    "No senior title or requirement above three years was detected.");
            case UNSPECIFIED -> {
            }
        }
        if (job.experienceMax() != null) {
            reasons.add("The stated experience range tops out at "
                    + job.experienceMax() + " years.");
        }
        if (opportunityType == OpportunityType.FULL_TIME) {
            reasons.add("The posting identifies the role as full-time.");
        }
        if (degreeRequirement == DegreeRequirement.NO_DEGREE_REQUIRED) {
            reasons.add("The posting accepts equivalent experience or says a degree is not required.");
        }
        if (sponsorshipStatus == SponsorshipStatus.AVAILABLE) {
            reasons.add("The posting indicates visa sponsorship is available.");
        }
        if (job.workplaceType() != WorkplaceType.UNKNOWN) {
            reasons.add("The workplace arrangement is identified as "
                    + job.workplaceType().name().toLowerCase(Locale.ROOT).replace('_', '-') + ".");
        }
        if (job.publishedAt() != null
                && !job.publishedAt().isBefore(Instant.now().minus(7, ChronoUnit.DAYS))) {
            reasons.add("The provider reports this posting as fresh within the last seven days.");
        }
        return List.copyOf(reasons);
    }

    private List<String> cautions(
            DiscoveredJob job,
            DegreeRequirement degreeRequirement,
            SponsorshipStatus sponsorshipStatus) {
        List<String> cautions = new ArrayList<>();
        if (job.experienceMax() == null) {
            cautions.add("The posting does not state a clear experience range.");
        }
        if (degreeRequirement == DegreeRequirement.BACHELORS_REQUIRED) {
            cautions.add("A bachelor's degree appears to be required.");
        } else if (degreeRequirement == DegreeRequirement.ADVANCED_DEGREE_REQUIRED) {
            cautions.add("An advanced degree appears to be required.");
        }
        if (sponsorshipStatus == SponsorshipStatus.NOT_AVAILABLE) {
            cautions.add("The posting says visa sponsorship is not available.");
        } else if (sponsorshipStatus == SponsorshipStatus.NOT_STATED) {
            cautions.add("Visa sponsorship is not specified.");
        }
        if (job.publishedAt() == null) {
            cautions.add("The provider does not supply a reliable posting date.");
        }
        return List.copyOf(cautions);
    }

    private String normalize(String value) {
        return nullToEmpty(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
