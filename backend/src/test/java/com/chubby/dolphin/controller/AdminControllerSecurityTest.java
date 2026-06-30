package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.LeadRepository;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import com.chubby.dolphin.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerSecurityTest {

    private AuditLogRepository auditRepo;
    private UserRepository userRepo;
    private CampaignRepository campaignRepo;
    private LeadRepository leadRepo;
    private BrainEventRepository brainEventRepo;
    private WorkspaceRepository workspaceRepo;
    private AccessControlService access;
    private AuditLogService auditLogService;
    private AdminController controller;
    private User orgAdmin;

    @BeforeEach
    void setUp() {
        auditRepo = mock(AuditLogRepository.class);
        userRepo = mock(UserRepository.class);
        campaignRepo = mock(CampaignRepository.class);
        leadRepo = mock(LeadRepository.class);
        brainEventRepo = mock(BrainEventRepository.class);
        workspaceRepo = mock(WorkspaceRepository.class);
        access = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        controller = new AdminController(auditRepo, userRepo, campaignRepo, leadRepo, brainEventRepo, workspaceRepo, access, auditLogService);

        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org 1");
        org.setPlan("AGENCY");
        orgAdmin = new User();
        orgAdmin.setId("admin-1");
        orgAdmin.setEmail("admin@org.test");
        orgAdmin.setRole("ORG_ADMIN");
        orgAdmin.setOrganization(org);
        orgAdmin.setAccountId("ws-1");

        when(access.currentUser()).thenReturn(orgAdmin);
        when(access.currentOrganizationId()).thenReturn("org-1");
    }

    @Test
    void organizationAdminUsersEndpointIsOrganizationScoped() {
        when(access.isSystemAdmin()).thenReturn(false);
        when(userRepo.findByOrganizationId("org-1")).thenReturn(List.of(orgAdmin));

        ResponseEntity<?> response = controller.users();

        assertEquals(200, response.getStatusCode().value());
        List<?> users = (List<?>) response.getBody();
        assertNotNull(users);
        assertEquals(1, users.size());
        verify(userRepo, never()).findAll();
        verify(auditLogService).record(eq(orgAdmin), any(), eq("ws-1"), eq("ADMIN_USERS_VIEWED"), eq("User"), eq("org-1"), any());
    }

    @Test
    void organizationAdminStatsAreOrganizationScoped() {
        Workspace workspace = new Workspace();
        workspace.setId("ws-1");
        workspace.setOrganization(orgAdmin.getOrganization());
        when(access.isSystemAdmin()).thenReturn(false);
        when(workspaceRepo.findByOrganizationId("org-1")).thenReturn(List.of(workspace));
        when(userRepo.countByOrganizationId("org-1")).thenReturn(3L);
        when(campaignRepo.countByWorkspaceIdIn(List.of("ws-1"))).thenReturn(4L);
        when(leadRepo.countByWorkspaceIdIn(List.of("ws-1"))).thenReturn(5L);
        when(brainEventRepo.countByWorkspaceIdIn(List.of("ws-1"))).thenReturn(6L);

        ResponseEntity<?> response = controller.stats();

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("organization", body.get("scope"));
        assertEquals(3L, body.get("total_users"));
        verify(userRepo, never()).count();
        verify(campaignRepo, never()).count();
        verify(leadRepo, never()).count();
        verify(brainEventRepo, never()).count();
    }

    @Test
    void systemAdminCanSeePlatformStats() {
        orgAdmin.setRole("SYSTEM_ADMIN");
        when(access.isSystemAdmin()).thenReturn(true);
        when(userRepo.count()).thenReturn(10L);

        ResponseEntity<?> response = controller.stats();

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("platform", body.get("scope"));
        assertEquals(10L, body.get("total_users"));
    }
}
