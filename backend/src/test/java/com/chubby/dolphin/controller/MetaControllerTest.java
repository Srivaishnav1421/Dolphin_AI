package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.security.SecurityUtils;
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

    private MetaController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new MetaController(metaAdsService, sec);
    }

    @Test
    public void testGetAuthUrl() {
        when(sec.currentAccountId()).thenReturn("account-123");
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
        when(sec.currentAccountId()).thenReturn("account-123");
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
        when(sec.currentAccountId()).thenReturn("account-123");
        MetaConnection conn = new MetaConnection();
        when(metaAdsService.getConnectionsForAccount("account-123")).thenReturn(List.of(conn));

        ResponseEntity<List<MetaConnection>> response = controller.listConnections();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    public void testConnectionStatusConnected() {
        when(sec.currentAccountId()).thenReturn("account-123");
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
        when(sec.currentAccountId()).thenReturn("account-123");
        when(metaAdsService.getActiveConnection("account-123")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.connectionStatus();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("connected"));
    }
}
