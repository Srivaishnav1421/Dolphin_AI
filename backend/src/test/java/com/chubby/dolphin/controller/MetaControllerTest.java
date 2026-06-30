package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.LocalApprovalSafetyService;
import com.chubby.dolphin.service.MetaAdsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MetaControllerTest {

    @Mock private MetaAdsService metaAdsService;
    @Mock private SecurityUtils sec;
    @Mock private AccessControlService access;
    @Mock private LocalApprovalSafetyService localApprovalSafetyService;

    private MetaController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(localApprovalSafetyService.shouldRequireApprovalOnly(anyString())).thenReturn(false);
        controller = new MetaController(metaAdsService, sec, access, localApprovalSafetyService);
    }

    @Test
    public void testGetAuthUrl() {
        when(sec.currentWorkspaceId()).thenReturn("account-123");
        when(metaAdsService.getAuthorizationUrl(anyString())).thenReturn("https://facebook.com/oauth");

        ResponseEntity<?> response = controller.getAuthUrl();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("https://facebook.com/oauth", body.get("auth_url"));
        assertTrue(body.get("state").toString().startsWith("account-123|"));
    }

    @Test
    public void testHandleCallbackSuccess() {
        when(sec.currentWorkspaceId()).thenReturn("account-123");
        MetaConnection mockConn = new MetaConnection();
        mockConn.setId("conn-999");
        mockConn.setMetaAdAccountId("act_9999");
        mockConn.setAdAccountName("Test Ad Account");
        mockConn.setTokenStatus("VALID");
        mockConn.setAutoManageEnabled(true);

        when(metaAdsService.exchangeCodeForToken("valid-code", "account-123")).thenReturn(mockConn);

        ResponseEntity<?> response = controller.handleCallback(Map.of("code", "valid-code", "state", "account-123"));
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        assertEquals("conn-999", body.get("connection_id"));
        assertEquals("Test Ad Account", body.get("ad_account_name"));
    }

    @Test
    public void testHandleCallbackMissingCode() {
        ResponseEntity<?> response = controller.handleCallback(Map.of("state", "account-123"));
        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void testListConnections() {
        when(sec.currentWorkspaceId()).thenReturn("account-123");
        MetaConnection conn = new MetaConnection();
        when(metaAdsService.getConnectionsForAccount("account-123")).thenReturn(List.of(conn));

        ResponseEntity<List<MetaConnection>> response = controller.listConnections();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    public void testConnectionStatusConnected() {
        when(sec.currentWorkspaceId()).thenReturn("account-123");
        MetaConnection conn = new MetaConnection();
        conn.setMetaAdAccountId("act_888");
        conn.setAdAccountName("Demo Account");
        conn.setTokenStatus("VALID");
        conn.setAutoManageEnabled(true);

        when(metaAdsService.getActiveConnection("account-123")).thenReturn(Optional.of(conn));

        ResponseEntity<?> response = controller.connectionStatus();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("connected"));
        assertEquals("Demo Account", body.get("ad_account_name"));
    }

    @Test
    public void testConnectionStatusNotConnected() {
        when(sec.currentWorkspaceId()).thenReturn("account-123");
        when(metaAdsService.getActiveConnection("account-123")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.connectionStatus();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("connected"));
    }

    @Test
    public void testLaunchAdBlockedInLocalApprovalFirstMode() {
        when(sec.currentWorkspaceId()).thenReturn("account-123");
        when(localApprovalSafetyService.shouldRequireApprovalOnly("META_LAUNCH_AD")).thenReturn(true);
        when(localApprovalSafetyService.blockedMessage("Meta launch"))
                .thenReturn("Meta launch is disabled in local approval-first mode. No external execution was performed.");

        ResponseEntity<?> response = controller.launchAd(Map.of(
                "headline", "Test headline",
                "body_text", "Test body",
                "image_hash", "hash-123",
                "page_id", "page-123"
        ));

        assertEquals(403, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals(true, body.get("approval_required"));
        assertEquals(false, body.get("external_execution_allowed"));
        assertTrue(body.get("message").toString().contains("local approval-first mode"));
        verify(access).requireWorkspacePermission(Permission.CAMPAIGN_APPROVE_AI_ACTION);
        verify(localApprovalSafetyService).auditBlockedExecution(
                eq("account-123"),
                eq("META_LAUNCH_AD"),
                eq("MetaAd"),
                isNull(),
                contains("/api/meta/launch-ad")
        );
        verify(metaAdsService, never()).getActiveConnection(anyString());
        verify(metaAdsService, never()).launchAd(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble());
    }
}
