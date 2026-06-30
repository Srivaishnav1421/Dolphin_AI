package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportControllerSecurityTest {

    private ReportService reportService;
    private SecurityUtils securityUtils;
    private AccessControlService access;
    private AuditLogService auditLogService;
    private ReportController controller;
    private User actor;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        securityUtils = mock(SecurityUtils.class);
        access = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        controller = new ReportController(reportService, securityUtils, access, auditLogService);

        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org 1");
        org.setPlan("AGENCY");
        actor = new User();
        actor.setId("user-1");
        actor.setEmail("user@org.test");
        actor.setOrganization(org);

        when(securityUtils.currentWorkspaceId()).thenReturn("ws-1");
        when(securityUtils.currentEmail()).thenReturn("user@org.test");
        when(access.currentUser()).thenReturn(actor);
    }

    @Test
    void reportExportUsesCurrentWorkspaceAndAudits() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 15);
        when(reportService.generateCampaignReportPdf("ws-1", start, end)).thenReturn(new byte[]{1, 2, 3});

        var response = controller.downloadCampaignReport(start, end);

        assertEquals(200, response.getStatusCode().value());
        verify(access).requireWorkspacePermission(Permission.REPORT_EXPORT);
        verify(reportService).generateCampaignReportPdf("ws-1", start, end);
        verify(auditLogService).record(eq(actor), eq(actor.getOrganization()), eq("ws-1"),
                eq("REPORT_EXPORTED"), eq("CampaignReport"), eq("ws-1"),
                eq("format=pdf; start=2026-06-01; end=2026-06-15"));
    }
}
