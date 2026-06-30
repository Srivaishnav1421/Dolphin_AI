package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.repository.AdCreativeRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.security.TenantAccessService;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.chubby.dolphin.service.CreativeAIService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreativeControllerSecurityTest {

    private CreativeAIService creativeService;
    private SecurityUtils sec;
    private AccessControlService access;
    private AuditLogService auditLogService;
    private AdCreativeRepository creativeRepo;
    private CampaignRepository campaignRepo;
    private CreativeController controller;
    private User actor;

    @BeforeEach
    void setUp() {
        creativeService = mock(CreativeAIService.class);
        sec = mock(SecurityUtils.class);
        access = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        creativeRepo = mock(AdCreativeRepository.class);
        campaignRepo = mock(CampaignRepository.class);
        controller = new CreativeController(creativeService, sec, access, auditLogService, creativeRepo, campaignRepo);

        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org 1");
        org.setPlan("AGENCY");
        actor = new User();
        actor.setId("user-1");
        actor.setEmail("user@org.test");
        actor.setRole("MARKETER");
        actor.setOrganization(org);
        actor.setAccountId("ws-1");

        when(sec.currentWorkspaceId()).thenReturn("ws-1");
        when(access.currentUser()).thenReturn(actor);
        when(auditLogService.redact(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listUsesCurrentWorkspaceOnly() {
        AdCreative creative = new AdCreative();
        creative.setId("creative-1");
        creative.setWorkspaceId("ws-1");
        when(creativeRepo.findByWorkspaceId("ws-1")).thenReturn(List.of(creative));

        var response = controller.list(null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(creative), response.getBody());
        verify(access).requireWorkspacePermission(Permission.CREATIVE_READ);
        verify(creativeRepo).findByWorkspaceId("ws-1");
    }

    @Test
    void viewerCannotGenerateWhenPermissionDenied() {
        org.mockito.Mockito.doThrow(new TenantAccessService.TenantAccessDeniedException("denied"))
                .when(access).requireWorkspacePermission(Permission.CREATIVE_GENERATE);

        assertThrows(TenantAccessService.TenantAccessDeniedException.class,
                () -> controller.generate(Map.of("product", "Service")));
    }

    @Test
    void rewriteCannotReachAnotherWorkspaceCreative() {
        when(creativeRepo.findByIdAndWorkspaceId("creative-other", "ws-1")).thenReturn(Optional.empty());

        var response = controller.rewrite("creative-other", Map.of("platform", "INSTAGRAM_STORY"));

        assertEquals(404, response.getStatusCode().value());
        verify(access).requireWorkspacePermission(Permission.CREATIVE_GENERATE);
    }

    @Test
    void generationCreatesAuditLog() {
        AdCreative generated = new AdCreative();
        generated.setId("creative-1");
        generated.setWorkspaceId("ws-1");
        when(creativeService.generateAdCopy(eq("ws-1"), eq(null), eq("Premium service"), eq(""), eq("professional"),
                eq("FACEBOOK_FEED"), eq("en"), eq("BALANCED"))).thenReturn(List.of(generated));

        var response = controller.generate(Map.of("product", "Premium service"));

        assertEquals(200, response.getStatusCode().value());
        verify(auditLogService).record(eq(actor), eq(actor.getOrganization()), eq("ws-1"),
                eq("CREATIVE_GENERATED"), eq("AdCreative"), eq("ws-1"), any());
    }
}
