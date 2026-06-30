package com.chubby.dolphin.controller;

import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.service.AnalyticsSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {

    @Test
    void summaryRequiresAnalyticsReadAndReturnsReadOnlyWorkspaceSummary() {
        AnalyticsSummaryService service = mock(AnalyticsSummaryService.class);
        AccessControlService access = mock(AccessControlService.class);
        when(access.currentWorkspaceId()).thenReturn("500badfa-4911-4eb3-88b5-c35674d55fbe");

        AnalyticsSummaryService.AnalyticsSummary summary = new AnalyticsSummaryService.AnalyticsSummary(
                "500badfa-4911-4eb3-88b5-c35674d55fbe",
                LocalDateTime.now(),
                new AnalyticsSummaryService.CampaignSummary(1, 1, 0, 0, 1000, 100, 300, 3, 50, "campaigns", false),
                new AnalyticsSummaryService.LeadSummary(2, 1, 1, 0, 0, 1, 0.7, "leads", false),
                new AnalyticsSummaryService.ApprovalSummary(3, 1, 1, 1, 0, "approval_items", false),
                new AnalyticsSummaryService.ContentFactorySummary(1, 3, 1, 1, 1, 82, List.of("content_factory_items", "content_factory_variants"), false),
                new AnalyticsSummaryService.AdBrainSummary(1, LocalDateTime.now(), 2, 2, 1, 0, 1, 1, "ad_brain_runs", false),
                new AnalyticsSummaryService.RiskOpportunitySummary(4, 1, 0, 1, 2, LocalDateTime.now(), "campaign_math_evaluations", false),
                new AnalyticsSummaryService.EmptyState(false, "Analytics is based only on recorded workspace data."),
                true
        );
        when(service.summary("500badfa-4911-4eb3-88b5-c35674d55fbe")).thenReturn(summary);

        AnalyticsController controller = new AnalyticsController(service, access);
        ResponseEntity<AnalyticsSummaryService.AnalyticsSummary> response = controller.summary();

        verify(access).requireWorkspacePermission(Permission.ANALYTICS_READ);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().readOnly());
        assertEquals("campaigns", response.getBody().campaignSummary().sourceTable());
    }
}
