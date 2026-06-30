package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WorkspaceConfigControllerTest {

    @Mock private WorkspaceConfigRepository configRepo;
    @Mock private SecurityUtils sec;

    private WorkspaceConfigController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new WorkspaceConfigController(configRepo, sec);
    }

    @Test
    public void testGetConfigExisting() {
        when(sec.currentAccountId()).thenReturn("ws-123");
        
        WorkspaceConfig config = new WorkspaceConfig();
        config.setWorkspaceId("ws-123");
        config.setWhatsappPhoneId("9999");
        config.setWhatsappToken("secret-token");
        config.setWhatsappVerifyToken("verify-secret");
        config.setBrandName("Chubby");

        when(configRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(config));

        ResponseEntity<Map<String, Object>> response = controller.getConfig();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("9999", response.getBody().get("whatsappPhoneId"));
        assertEquals("Chubby", response.getBody().get("brandName"));
        assertEquals(true, response.getBody().get("whatsappTokenConfigured"));
        assertEquals(true, response.getBody().get("whatsappVerifyTokenConfigured"));
        assertFalse(response.getBody().containsKey("whatsappToken"));
        assertFalse(response.getBody().containsKey("whatsappVerifyToken"));
        assertFalse(response.getBody().toString().contains("secret-token"));
        assertFalse(response.getBody().toString().contains("verify-secret"));
    }

    @Test
    public void testGetConfigDoesNotCreateOnRead() {
        when(sec.currentAccountId()).thenReturn("ws-123");
        when(configRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.getConfig();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ws-123", response.getBody().get("workspaceId"));
        verify(configRepo, never()).save(any(WorkspaceConfig.class));
    }

    @Test
    public void testUpdateConfigReturnsSafeResponseAndPreservesSecretsWhenAbsent() {
        when(sec.currentAccountId()).thenReturn("ws-123");
        
        WorkspaceConfig existing = new WorkspaceConfig();
        existing.setWorkspaceId("ws-123");
        existing.setWhatsappToken("existing-secret");
        existing.setWhatsappVerifyToken("existing-verify-secret");
        when(configRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(existing));

        Map<String, Object> body = new HashMap<>();
        body.put("whatsappPhoneId", "1111");
        body.put("legalName", "Legal Pvt Ltd");
        body.put("stateCode", "MH");

        WorkspaceConfig updatedMock = new WorkspaceConfig();
        updatedMock.setWorkspaceId("ws-123");
        updatedMock.setWhatsappPhoneId("1111");
        updatedMock.setWhatsappToken("existing-secret");
        updatedMock.setWhatsappVerifyToken("existing-verify-secret");
        updatedMock.setLegalName("Legal Pvt Ltd");
        updatedMock.setStateCode("MH");

        when(configRepo.save(any(WorkspaceConfig.class))).thenReturn(updatedMock);

        ResponseEntity<Map<String, Object>> response = controller.updateConfig(body);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("1111", response.getBody().get("whatsappPhoneId"));
        assertEquals("Legal Pvt Ltd", response.getBody().get("legalName"));
        assertEquals(true, response.getBody().get("whatsappTokenConfigured"));
        assertFalse(response.getBody().containsKey("whatsappToken"));
        assertFalse(response.getBody().containsKey("whatsappVerifyToken"));
        assertEquals("existing-secret", existing.getWhatsappToken());
        assertEquals("existing-verify-secret", existing.getWhatsappVerifyToken());
    }

    @Test
    public void testUpdateConfigStoresExplicitNewSecretsWithoutReturningThem() {
        when(sec.currentAccountId()).thenReturn("ws-123");

        WorkspaceConfig existing = new WorkspaceConfig();
        existing.setWorkspaceId("ws-123");
        when(configRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(existing));

        Map<String, Object> body = new HashMap<>();
        body.put("whatsappToken", "new-secret-token");
        body.put("whatsappVerifyToken", "new-verify-secret");

        WorkspaceConfig updatedMock = new WorkspaceConfig();
        updatedMock.setWorkspaceId("ws-123");
        updatedMock.setWhatsappToken("new-secret-token");
        updatedMock.setWhatsappVerifyToken("new-verify-secret");
        when(configRepo.save(any(WorkspaceConfig.class))).thenReturn(updatedMock);

        ResponseEntity<Map<String, Object>> response = controller.updateConfig(body);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("new-secret-token", existing.getWhatsappToken());
        assertEquals("new-verify-secret", existing.getWhatsappVerifyToken());
        assertFalse(response.getBody().toString().contains("new-secret-token"));
        assertFalse(response.getBody().toString().contains("new-verify-secret"));
    }
}
