package com.chubby.dolphin.workspace.policy;

import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.rbac.RolePermissionService;
import com.chubby.dolphin.security.TenantAccessService;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceAccessPolicy {

    private final TenantAccessService tenantAccessService;
    private final RolePermissionService rolePermissionService;

    public WorkspaceAccessPolicy(TenantAccessService tenantAccessService,
                                 RolePermissionService rolePermissionService) {
        this.tenantAccessService = tenantAccessService;
        this.rolePermissionService = rolePermissionService;
    }

    public void require(User user, String workspaceId, Permission permission) {
        if (user == null || user.getEmail() == null) {
            throw new TenantAccessService.TenantAccessDeniedException("Authenticated user required");
        }
        if (!tenantAccessService.canAccessWorkspace(user.getEmail(), workspaceId)) {
            throw new TenantAccessService.TenantAccessDeniedException("Workspace access denied");
        }
        String role = tenantAccessService.workspaceRole(user.getEmail(), workspaceId).orElse(user.getRole());
        if (!rolePermissionService.hasPermission(role, permission)) {
            throw new TenantAccessService.TenantAccessDeniedException("Permission denied: " + permission.name());
        }
    }

    public boolean can(User user, String workspaceId, Permission permission) {
        try {
            require(user, workspaceId, permission);
            return true;
        } catch (TenantAccessService.TenantAccessDeniedException ex) {
            return false;
        }
    }
}
