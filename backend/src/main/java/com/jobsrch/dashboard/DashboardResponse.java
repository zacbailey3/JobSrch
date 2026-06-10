package com.jobsrch.dashboard;

import java.util.Map;

public record DashboardResponse(
        long savedJobs,
        long totalApplications,
        Map<String, Long> applicationsByStatus) {
}
