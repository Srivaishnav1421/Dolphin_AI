package com.chubby.dolphin.tenant;

import com.chubby.dolphin.security.TenantAccessService;
import com.chubby.dolphin.security.TenantContext;
import org.springframework.stereotype.Component;

@Component
public class TenantContextResolver {

    private final TenantAccessService tenantAccessService;

    public TenantContextResolver(TenantAccessService tenantAccessService) {
        this.tenantAccessService = tenantAccessService;
    }

    public void bindAuthenticatedWorkspace(String email, String workspaceId, boolean required) {
        if (required && (workspaceId == null || workspaceId.isBlank())) {
            throw new TenantAccessService.TenantAccessDeniedException("Workspace context required");
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            if (!tenantAccessService.canAccessWorkspace(email, workspaceId)) {
                throw new TenantAccessService.TenantAccessDeniedException("Workspace access denied");
            }
            TenantContext.setCurrentTenant(workspaceId);
        }
    }

    public void clear() {
        TenantContext.clear();
    }
}
