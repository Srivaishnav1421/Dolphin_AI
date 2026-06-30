package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.rbac.RolePermissionService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AccessControlService {

    private static final Set<String> SYSTEM_ROLES = Set.of("SYSTEM_ADMIN", "PLATFORM_ADMIN");

    private final SecurityUtils securityUtils;
    private final TenantAccessService tenantAccessService;
    private final RolePermissionService rolePermissionService;

    public AccessControlService(SecurityUtils securityUtils,
                                TenantAccessService tenantAccessService,
                                RolePermissionService rolePermissionService) {
        this.securityUtils = securityUtils;
        this.tenantAccessService = tenantAccessService;
        this.rolePermissionService = rolePermissionService;
    }

    public User currentUser() {
        return securityUtils.currentUser();
    }

    public String currentWorkspaceId() {
        return securityUtils.currentWorkspaceId();
    }

    public String currentOrganizationId() {
        User user = currentUser();
        if (user.getOrganization() == null) {
            throw new TenantAccessService.TenantAccessDeniedException("Organization context required");
        }
        return user.getOrganization().getId();
    }

    public void requireSystemAdmin() {
        User user = currentUser();
        if (!isSystemAdmin()) {
            throw new TenantAccessService.TenantAccessDeniedException("System administrator access required");
        }
    }

    public boolean isSystemAdmin() {
        User user = currentUser();
        return SYSTEM_ROLES.contains(normalize(user.getRole()));
    }

    public void requireWorkspacePermission(Permission permission) {
        User user = currentUser();
        String workspaceId = currentWorkspaceId();
        tenantAccessService.requireWorkspaceAccess(user.getEmail(), workspaceId);
        String effectiveRole = tenantAccessService.workspaceRole(user.getEmail(), workspaceId)
                .orElse(user.getRole());
        if (!rolePermissionService.hasPermission(effectiveRole, permission)
                && !rolePermissionService.hasPermission(user.getRole(), permission)) {
            throw new TenantAccessService.TenantAccessDeniedException("Permission denied: " + permission.name().toLowerCase());
        }
    }

    public void requireWorkspacePermission(String permission) {
        requireWorkspacePermission(Permission.fromCode(permission));
    }

    public void requireSameWorkspace(String resourceWorkspaceId) {
        String workspaceId = currentWorkspaceId();
        if (resourceWorkspaceId == null || !resourceWorkspaceId.equals(workspaceId)) {
            throw new TenantAccessService.TenantAccessDeniedException("Workspace resource access denied");
        }
    }

    public void requireCurrentOrganization(String organizationId) {
        if (organizationId == null || !organizationId.equals(currentOrganizationId())) {
            throw new TenantAccessService.TenantAccessDeniedException("Organization resource access denied");
        }
    }

    public boolean hasWorkspacePermission(Permission permission) {
        try {
            requireWorkspacePermission(permission);
            return true;
        } catch (TenantAccessService.TenantAccessDeniedException ex) {
            return false;
        }
    }

    private String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }
}
