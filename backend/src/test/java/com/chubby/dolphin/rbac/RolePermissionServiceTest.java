package com.chubby.dolphin.rbac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RolePermissionServiceTest {

    private final RolePermissionService service = new RolePermissionService();

    @Test
    void viewerIsReadOnlyForCampaignsAndLeads() {
        assertTrue(service.hasPermission("VIEWER", Permission.CAMPAIGN_READ));
        assertTrue(service.hasPermission("VIEWER", Permission.LEAD_READ));
        assertFalse(service.hasPermission("VIEWER", Permission.CAMPAIGN_CREATE));
        assertFalse(service.hasPermission("VIEWER", Permission.LEAD_UPDATE));
        assertFalse(service.hasPermission("VIEWER", Permission.WALLET_MANAGE));
    }

    @Test
    void salesAgentCanWorkLeadsButNotManageCampaignsOrBilling() {
        assertTrue(service.hasPermission("SALES_AGENT", Permission.LEAD_READ));
        assertTrue(service.hasPermission("SALES_AGENT", Permission.LEAD_UPDATE));
        assertTrue(service.hasPermission("SALES_AGENT", Permission.LEAD_ACTIVITY_ADD));
        assertFalse(service.hasPermission("SALES_AGENT", Permission.CAMPAIGN_UPDATE));
        assertFalse(service.hasPermission("SALES_AGENT", Permission.BILLING_MANAGE));
    }

    @Test
    void ownerHasOrganizationManagementButViewerDoesNot() {
        assertTrue(service.hasPermission("OWNER", Permission.INTEGRATION_MANAGE));
        assertTrue(service.hasPermission("OWNER", Permission.AI_ROUTE_MANAGE));
        assertFalse(service.hasPermission("VIEWER", Permission.INTEGRATION_MANAGE));
        assertFalse(service.hasPermission("VIEWER", Permission.AI_ROUTE_MANAGE));
    }

    @Test
    void adBrainRunIsRestrictedButReadCanBeReadonly() {
        assertTrue(service.hasPermission("OWNER", Permission.AD_BRAIN_RUN));
        assertTrue(service.hasPermission("MANAGER", Permission.AD_BRAIN_RUN));
        assertTrue(service.hasPermission("MARKETER", Permission.AD_BRAIN_RUN));
        assertFalse(service.hasPermission("SALES_AGENT", Permission.AD_BRAIN_RUN));
        assertFalse(service.hasPermission("VIEWER", Permission.AD_BRAIN_RUN));
        assertTrue(service.hasPermission("VIEWER", Permission.AD_BRAIN_READ));
        assertTrue(service.hasPermission("CLIENT_VIEWER", Permission.AD_BRAIN_READ));
    }
}
