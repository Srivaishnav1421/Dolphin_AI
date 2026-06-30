package com.chubby.dolphin.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storesOrganizationWorkspaceAndUserContext() {
        TenantContext.setCurrentTenant("org-1", "ws-1", "user-1");

        assertEquals("org-1", TenantContext.getCurrentOrganizationId());
        assertEquals("ws-1", TenantContext.getCurrentWorkspaceId());
        assertEquals("ws-1", TenantContext.getCurrentTenant());
        assertEquals("user-1", TenantContext.getCurrentUserId());
    }

    @Test
    void clearRemovesAllContext() {
        TenantContext.setCurrentTenant("org-1", "ws-1", "user-1");
        TenantContext.clear();

        assertNull(TenantContext.getCurrentOrganizationId());
        assertNull(TenantContext.getCurrentWorkspaceId());
        assertNull(TenantContext.getCurrentUserId());
    }
}
