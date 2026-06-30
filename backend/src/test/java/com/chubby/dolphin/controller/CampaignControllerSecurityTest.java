package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.mathengine.CampaignMathEvaluationService;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.security.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignControllerSecurityTest {

    private CampaignRepository repo;
    private SecurityUtils sec;
    private AccessControlService access;
    private AuditLogService auditLogService;
    private CampaignMathEvaluationService mathEvaluationService;
    private CampaignController controller;
    private User owner;

    @BeforeEach
    void setUp() {
        repo = mock(CampaignRepository.class);
        sec = mock(SecurityUtils.class);
        access = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        mathEvaluationService = mock(CampaignMathEvaluationService.class);
        controller = new CampaignController(repo, sec, access, auditLogService, mathEvaluationService);

        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org 1");
        org.setPlan("AGENCY");
        owner = new User();
        owner.setId("owner-1");
        owner.setEmail("owner@org.test");
        owner.setRole("OWNER");
        owner.setOrganization(org);
        owner.setAccountId("ws-1");

        when(sec.currentWorkspaceId()).thenReturn("ws-1");
        when(access.currentUser()).thenReturn(owner);
    }

    @Test
    void viewerCannotCreateCampaignWhenPermissionDenied() {
        org.mockito.Mockito.doThrow(new TenantAccessService.TenantAccessDeniedException("denied"))
                .when(access).requireWorkspacePermission(Permission.CAMPAIGN_CREATE);

        assertThrows(TenantAccessService.TenantAccessDeniedException.class,
                () -> controller.create(new Campaign()));
    }

    @Test
    void cannotReadCampaignFromAnotherWorkspaceByIdTampering() {
        when(repo.findByIdAndWorkspaceId("camp-other", "ws-1")).thenReturn(Optional.empty());

        var response = controller.get("camp-other");

        assertEquals(404, response.getStatusCode().value());
        verify(access).requireWorkspacePermission(Permission.CAMPAIGN_READ);
    }

    @Test
    void campaignCreateScopesToCurrentWorkspaceAndAudits() {
        Campaign campaign = new Campaign();
        campaign.setName("Launch");
        when(repo.save(any(Campaign.class))).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId("camp-1");
            return saved;
        });

        var response = controller.create(campaign);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ws-1", response.getBody().getWorkspaceId());
        verify(access).requireWorkspacePermission(Permission.CAMPAIGN_CREATE);
        verify(auditLogService).record(eq(owner), eq(owner.getOrganization()), eq("ws-1"),
                eq("CAMPAIGN_CREATED"), eq("Campaign"), eq("camp-1"), eq("Campaign created"));
    }
}
