package com.chubby.dolphin.controller;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.UserWorkspaceRole;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.UserWorkspaceRoleRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.security.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceTeamControllerSecurityTest {

    private UserRepository userRepo;
    private UserWorkspaceRoleRepository roleRepo;
    private SecurityUtils sec;
    private AccessControlService access;
    private AuditLogService auditLogService;
    private WorkspaceTeamController controller;
    private User actor;
    private User target;
    private UserWorkspaceRole targetMembership;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        roleRepo = mock(UserWorkspaceRoleRepository.class);
        sec = mock(SecurityUtils.class);
        access = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        controller = new WorkspaceTeamController(userRepo, roleRepo, sec, access, auditLogService);

        Organization org = new Organization();
        org.setId("org-1");
        org.setName("Org 1");
        org.setPlan("AGENCY");
        Workspace workspace = new Workspace();
        workspace.setId("ws-1");
        workspace.setOrganization(org);

        actor = user("owner-1", "owner@org.test", "OWNER", org);
        target = user("user-1", "user@org.test", "VIEWER", org);
        targetMembership = new UserWorkspaceRole();
        targetMembership.setUser(target);
        targetMembership.setWorkspace(workspace);
        targetMembership.setRole("VIEWER");
        targetMembership.setOrganization(org);

        when(sec.currentWorkspaceId()).thenReturn("ws-1");
        when(sec.currentUser()).thenReturn(actor);
        when(userRepo.findById("user-1")).thenReturn(Optional.of(target));
        when(roleRepo.findByUserIdAndWorkspaceId("user-1", "ws-1")).thenReturn(Optional.of(targetMembership));
        UserWorkspaceRole actorMembership = new UserWorkspaceRole();
        actorMembership.setUser(actor);
        actorMembership.setWorkspace(workspace);
        actorMembership.setRole("OWNER");
        when(roleRepo.findByUserIdAndWorkspaceId("owner-1", "ws-1")).thenReturn(Optional.of(actorMembership));
        when(roleRepo.save(any(UserWorkspaceRole.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void workspaceRoleUpdateDoesNotMutateGlobalUserRole() {
        var response = controller.updateRole("user-1", new WorkspaceTeamController.RoleUpdateRequest("MANAGER"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("VIEWER", target.getRole());
        assertEquals("MANAGER", targetMembership.getRole());
        verify(userRepo, never()).save(any());
        verify(roleRepo).save(targetMembership);
        verify(auditLogService).record(eq(actor), eq(actor.getOrganization()), eq("ws-1"),
                eq("WORKSPACE_MEMBER_ROLE_CHANGED"), eq("UserWorkspaceRole"), eq("user-1"), any());
    }

    @Test
    void unauthorizedUserCannotUpdateWorkspaceRole() {
        org.mockito.Mockito.doThrow(new TenantAccessService.TenantAccessDeniedException("denied"))
                .when(access).requireWorkspacePermission(Permission.MEMBER_MANAGE);

        assertThrows(TenantAccessService.TenantAccessDeniedException.class,
                () -> controller.updateRole("user-1", new WorkspaceTeamController.RoleUpdateRequest("MANAGER")));
    }

    private User user(String id, String email, String role, Organization org) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(email);
        user.setRole(role);
        user.setOrganization(org);
        user.setAccountId("ws-1");
        user.setActive(true);
        return user;
    }
}
