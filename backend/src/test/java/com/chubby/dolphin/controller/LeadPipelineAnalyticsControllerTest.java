package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.SystemAlert;
import com.chubby.dolphin.repository.LeadPipelineEventRepository;
import com.chubby.dolphin.repository.SystemAlertRepository;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LeadPipelineAnalyticsControllerTest {

    @Mock private LeadPipelineEventRepository eventRepo;
    @Mock private SystemAlertRepository alertRepo;
    @Mock private SecurityUtils sec;
    @Mock private AccessControlService access;

    private LeadPipelineAnalyticsController controller;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new LeadPipelineAnalyticsController(eventRepo, alertRepo, sec, access);
        when(sec.currentWorkspaceId()).thenReturn("workspace-test");
    }

    @Test
    public void testGetPipelineHealthStatsEmpty() {
        // Arrange
        when(eventRepo.countByWorkspaceIdAndEventType(anyString(), anyString())).thenReturn(0L);
        when(eventRepo.countByWorkspaceIdAndStatus(anyString(), anyString())).thenReturn(0L);
        when(alertRepo.findByWorkspaceIdAndResolvedOrderByCreatedAtDesc("workspace-test", false))
                .thenReturn(Collections.emptyList());

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getPipelineHealth();

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(0L, body.get("totalLeads"));
        assertEquals(100.0, body.get("webhookSuccessRate"));
        assertEquals(100.0, body.get("leadCreationRate"));
        assertEquals(100.0, body.get("whatsappDeliveryRate"));
        assertEquals(0.0, body.get("replyRate"));
        assertEquals(0.0, body.get("pipelineFailureRate"));
        assertTrue(((List<?>) body.get("activeAlerts")).isEmpty());
    }

    @Test
    public void testGetPipelineHealthStatsCalculated() {
        // Arrange
        when(eventRepo.countByWorkspaceIdAndEventType("workspace-test", "WEBHOOK_RECEIVED")).thenReturn(10L);
        when(eventRepo.countByWorkspaceIdAndEventType("workspace-test", "WORKSPACE_RESOLVED")).thenReturn(9L);
        when(eventRepo.countByWorkspaceIdAndEventType("workspace-test", "LEAD_CREATED")).thenReturn(8L);
        when(eventRepo.countByWorkspaceIdAndEventType("workspace-test", "WHATSAPP_SENT")).thenReturn(8L);
        when(eventRepo.countByWorkspaceIdAndEventType("workspace-test", "WHATSAPP_DELIVERED")).thenReturn(7L);
        when(eventRepo.countByWorkspaceIdAndEventType("workspace-test", "WHATSAPP_REPLIED")).thenReturn(2L);
        
        when(eventRepo.countByWorkspaceIdAndStatus("workspace-test", "FAILED")).thenReturn(1L);

        SystemAlert mockAlert = new SystemAlert();
        mockAlert.setWorkspaceId("workspace-test");
        mockAlert.setAlertType("TEST_ANOMALY");
        mockAlert.setMessage("Alert msg");
        mockAlert.setSeverity("HIGH");

        when(alertRepo.findByWorkspaceIdAndResolvedOrderByCreatedAtDesc("workspace-test", false))
                .thenReturn(List.of(mockAlert));

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getPipelineHealth();

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(8L, body.get("totalLeads"));
        
        // webhookSuccessRate = 9/10 = 90.0
        assertEquals(90.0, body.get("webhookSuccessRate"));
        
        // leadCreationRate = 8/9 = 88.9
        assertEquals(88.9, body.get("leadCreationRate"));
        
        // whatsappDeliveryRate = 7/8 = 87.5
        assertEquals(87.5, body.get("whatsappDeliveryRate"));
        
        // replyRate = 2/7 = 28.6
        assertEquals(28.6, body.get("replyRate"));
        
        // active alerts
        List<?> alertsList = (List<?>) body.get("activeAlerts");
        assertEquals(1, alertsList.size());
    }
}
