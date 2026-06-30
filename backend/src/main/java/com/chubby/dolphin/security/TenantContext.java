package com.chubby.dolphin.security;

/**
 * Tenant Context — ThreadLocal context holder to store and clear
 * the current request's authenticated tenant identity.
 */
public class TenantContext {

    private static final ThreadLocal<Context> CURRENT_CONTEXT = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        setCurrentTenant(null, tenantId, null);
    }

    public static void setCurrentTenant(String organizationId, String workspaceId, String userId) {
        CURRENT_CONTEXT.set(new Context(organizationId, workspaceId, userId));
    }

    public static String getCurrentTenant() {
        return getCurrentWorkspaceId();
    }

    public static String getCurrentOrganizationId() {
        Context context = CURRENT_CONTEXT.get();
        return context != null ? context.organizationId() : null;
    }

    public static String getCurrentWorkspaceId() {
        Context context = CURRENT_CONTEXT.get();
        return context != null ? context.workspaceId() : null;
    }

    public static String getCurrentUserId() {
        Context context = CURRENT_CONTEXT.get();
        return context != null ? context.userId() : null;
    }

    public static void clear() {
        CURRENT_CONTEXT.remove();
    }

    private record Context(String organizationId, String workspaceId, String userId) {}
}
