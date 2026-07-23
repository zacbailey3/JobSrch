package com.jobsrch.discovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import-status")
public class JobImportStatusController {

    private final JobImportStatusService statusService;

    public JobImportStatusController(JobImportStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping
    ImportStatusResponse status() {
        return statusService.status();
    }
}
