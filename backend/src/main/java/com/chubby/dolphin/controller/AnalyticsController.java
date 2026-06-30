package com.chubby.dolphin.controller;

import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.service.AnalyticsSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsSummaryService analyticsSummaryService;
    private final AccessControlService access;

    public AnalyticsController(AnalyticsSummaryService analyticsSummaryService,
                               AccessControlService access) {
        this.analyticsSummaryService = analyticsSummaryService;
        this.access = access;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryService.AnalyticsSummary> summary() {
        access.requireWorkspacePermission(Permission.ANALYTICS_READ);
        return ResponseEntity.ok(analyticsSummaryService.summary(access.currentWorkspaceId()));
    }
}
