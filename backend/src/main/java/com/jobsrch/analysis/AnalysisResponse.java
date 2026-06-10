package com.jobsrch.analysis;

import java.util.List;
import java.util.UUID;

public record AnalysisResponse(
        UUID resumeId,
        UUID jobPostingId,
        int overallScore,
        int keywordScore,
        int experienceScore,
        int structureScore,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> suggestions) {
}
