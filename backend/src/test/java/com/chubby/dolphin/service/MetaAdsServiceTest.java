package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MetaAdsServiceTest {

    @Mock
    private MetaConnectionRepository metaConnRepo;

    private MetaAdsService metaAdsService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // We initialize the service. The WebClient isn't used for local setting logic tests,
        // so passing mock dependencies is sufficient. We also mock repository operations.
        metaAdsService = new MetaAdsService(
                metaConnRepo,
                null,
                null,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                "https://graph.facebook.com"
        );
    }

    @Test
    public void testUpdateConnectionSettingsSuccess() {
        String accountId = "test-account";
        String connId = "conn-123";

        MetaConnection conn = new MetaConnection();
        conn.setId(connId);
        conn.setAccountId(accountId);
        conn.setAutoManageEnabled(false);
        conn.setMaxDailySpend(5000.0);

        when(metaConnRepo.findById(connId)).thenReturn(Optional.of(conn));
        when(metaConnRepo.save(any(MetaConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> settings = new HashMap<>();
        settings.put("auto_manage_enabled", true);
        settings.put("max_daily_spend", "12000.0");
        settings.put("pause_roas_threshold", "1.8");

        MetaConnection updated = metaAdsService.updateConnectionSettings(accountId, connId, settings);

        assertNotNull(updated);
        assertTrue(updated.isAutoManageEnabled());
        assertEquals(12000.0, updated.getMaxDailySpend());
        assertEquals(1.8, updated.getPauseRoasThreshold());
        verify(metaConnRepo, times(1)).save(conn);
    }

    @Test
    public void testUpdateConnectionSettingsAccessDenied() {
        String accountId = "test-account";
        String connId = "conn-123";

        MetaConnection conn = new MetaConnection();
        conn.setId(connId);
        conn.setAccountId("other-account"); // Mismatched account ID

        when(metaConnRepo.findById(connId)).thenReturn(Optional.of(conn));

        Map<String, Object> settings = new HashMap<>();
        settings.put("auto_manage_enabled", true);

        assertThrows(SecurityException.class, () -> {
            metaAdsService.updateConnectionSettings(accountId, connId, settings);
        });
        verify(metaConnRepo, never()).save(any(MetaConnection.class));
    }

    @Test
    public void testDisconnectConnectionSuccess() {
        String accountId = "test-account";
        String connId = "conn-123";

        MetaConnection conn = new MetaConnection();
        conn.setId(connId);
        conn.setAccountId(accountId);
        conn.setTokenStatus("VALID");

        when(metaConnRepo.findById(connId)).thenReturn(Optional.of(conn));
        when(metaConnRepo.save(any(MetaConnection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MetaConnection disconnected = metaAdsService.disconnectConnection(accountId, connId);

        assertNotNull(disconnected);
        assertEquals("REVOKED", disconnected.getTokenStatus());
        verify(metaConnRepo, times(1)).save(conn);
    }
}
