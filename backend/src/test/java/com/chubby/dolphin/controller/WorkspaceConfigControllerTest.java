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
        config.setBrandName("Chubby");

        when(configRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(config));

        ResponseEntity<WorkspaceConfig> response = controller.getConfig();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("9999", response.getBody().getWhatsappPhoneId());
        assertEquals("Chubby", response.getBody().getBrandName());
    }

    @Test
    public void testGetConfigCreatesNew() {
        when(sec.currentAccountId()).thenReturn("ws-123");
        when(configRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.empty());
        
        WorkspaceConfig savedMock = new WorkspaceConfig();
        savedMock.setWorkspaceId("ws-123");
        when(configRepo.save(any(WorkspaceConfig.class))).thenReturn(savedMock);

        ResponseEntity<WorkspaceConfig> response = controller.getConfig();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ws-123", response.getBody().getWorkspaceId());
        verify(configRepo, times(1)).save(any(WorkspaceConfig.class));
    }

    @Test
    public void testUpdateConfig() {
        when(sec.currentAccountId()).thenReturn("ws-123");
        
        WorkspaceConfig existing = new WorkspaceConfig();
        existing.setWorkspaceId("ws-123");
        when(configRepo.findByWorkspaceId("ws-123")).thenReturn(Optional.of(existing));

        Map<String, Object> body = new HashMap<>();
        body.put("whatsappPhoneId", "1111");
        body.put("whatsappToken", "secret-token");
        body.put("legalName", "Legal Pvt Ltd");
        body.put("stateCode", "MH");

        WorkspaceConfig updatedMock = new WorkspaceConfig();
        updatedMock.setWorkspaceId("ws-123");
        updatedMock.setWhatsappPhoneId("1111");
        updatedMock.setWhatsappToken("secret-token");
        updatedMock.setLegalName("Legal Pvt Ltd");
        updatedMock.setStateCode("MH");

        when(configRepo.save(any(WorkspaceConfig.class))).thenReturn(updatedMock);

        ResponseEntity<WorkspaceConfig> response = controller.updateConfig(body);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("1111", response.getBody().getWhatsappPhoneId());
        assertEquals("secret-token", response.getBody().getWhatsappToken());
        assertEquals("Legal Pvt Ltd", response.getBody().getLegalName());
    }
}
