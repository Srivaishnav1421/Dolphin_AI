package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.entity.UserWorkspaceRole;
import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.repository.UserRepository;
import com.chubby.dolphin.repository.UserWorkspaceRoleRepository;
import com.chubby.dolphin.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantAccessServiceTest {

    @Test
    void allowsWorkspaceInsideSameOrganization() {
        Organization organization = organization("org-1");
        User user = user("owner@agency.test", organization, true);
        Workspace workspace = workspace("ws-1", organization);

        UserRepository users = mock(UserRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        UserWorkspaceRoleRepository roles = mock(UserWorkspaceRoleRepository.class);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(workspaces.findById(workspace.getId())).thenReturn(Optional.of(workspace));

        TenantAccessService service = new TenantAccessService(users, workspaces, roles);

        assertTrue(service.canAccessWorkspace(user.getEmail(), workspace.getId()));
    }

    @Test
    void deniesWorkspaceFromDifferentOrganization() {
        User user = user("owner@agency.test", organization("org-1"), true);
        Workspace workspace = workspace("ws-2", organization("org-2"));

        UserRepository users = mock(UserRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        UserWorkspaceRoleRepository roles = mock(UserWorkspaceRoleRepository.class);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(workspaces.findById(workspace.getId())).thenReturn(Optional.of(workspace));

        TenantAccessService service = new TenantAccessService(users, workspaces, roles);

        assertFalse(service.canAccessWorkspace(user.getEmail(), workspace.getId()));
    }

    @Test
    void deniesInactiveUser() {
        Organization organization = organization("org-1");
        User user = user("disabled@agency.test", organization, false);
        Workspace workspace = workspace("ws-1", organization);

        UserRepository users = mock(UserRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        UserWorkspaceRoleRepository roles = mock(UserWorkspaceRoleRepository.class);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(workspaces.findById(workspace.getId())).thenReturn(Optional.of(workspace));

        TenantAccessService service = new TenantAccessService(users, workspaces, roles);

        assertFalse(service.canAccessWorkspace(user.getEmail(), workspace.getId()));
    }

    @Test
    void allowsNonAdminOnlyWithExplicitWorkspaceRole() {
        Organization organization = organization("org-1");
        User user = user("employee@agency.test", organization, true);
        user.setRole("EMPLOYEE");
        Workspace workspace = workspace("ws-2", organization);
        UserWorkspaceRole workspaceRole = new UserWorkspaceRole();
        workspaceRole.setUser(user);
        workspaceRole.setWorkspace(workspace);
        workspaceRole.setRole("EMPLOYEE");

        UserRepository users = mock(UserRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        UserWorkspaceRoleRepository roles = mock(UserWorkspaceRoleRepository.class);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(workspaces.findById(workspace.getId())).thenReturn(Optional.of(workspace));
        when(roles.findByUserIdAndWorkspaceId(user.getId(), workspace.getId())).thenReturn(Optional.of(workspaceRole));

        TenantAccessService service = new TenantAccessService(users, workspaces, roles);

        assertTrue(service.canAccessWorkspace(user.getEmail(), workspace.getId()));
    }

    @Test
    void deniesNonAdminWithoutWorkspaceRole() {
        Organization organization = organization("org-1");
        User user = user("employee@agency.test", organization, true);
        user.setRole("EMPLOYEE");
        Workspace workspace = workspace("ws-2", organization);

        UserRepository users = mock(UserRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        UserWorkspaceRoleRepository roles = mock(UserWorkspaceRoleRepository.class);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(workspaces.findById(workspace.getId())).thenReturn(Optional.of(workspace));
        when(roles.findByUserIdAndWorkspaceId(user.getId(), workspace.getId())).thenReturn(Optional.empty());

        TenantAccessService service = new TenantAccessService(users, workspaces, roles);

        assertFalse(service.canAccessWorkspace(user.getEmail(), workspace.getId()));
    }

    private Organization organization(String id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(id);
        organization.setPlan("AGENCY");
        return organization;
    }

    private User user(String email, Organization organization, boolean active) {
        User user = new User();
        user.setId(email);
        user.setEmail(email);
        user.setOrganization(organization);
        user.setActive(active);
        user.setRole("OWNER");
        return user;
    }

    private Workspace workspace(String id, Organization organization) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setName(id);
        workspace.setOrganization(organization);
        return workspace;
    }
}
