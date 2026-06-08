package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.LeadPipelineEvent;
import com.chubby.dolphin.entity.SystemAlert;
import com.chubby.dolphin.repository.LeadPipelineEventRepository;
import com.chubby.dolphin.repository.SystemAlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LeadPipelineTrackingServiceTest {

    @Mock private LeadPipelineEventRepository eventRepo;
    @Mock private SystemAlertRepository alertRepo;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter mockCounter;

    private LeadPipelineTrackingService service;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);
        service = new LeadPipelineTrackingService(eventRepo, alertRepo, meterRegistry);
    }

    @Test
    public void testRecordWebhookReceived() {
        service.recordWebhookReceived("workspace-123", "Raw Webhook Details");
        verify(eventRepo, times(1)).save(any(LeadPipelineEvent.class));
        verify(mockCounter, atLeastOnce()).increment();
    }

    @Test
    public void testRecordFailure() {
        service.recordFailure("workspace-123", "lead-456", "PIPELINE_FAILED", "Detail Msg");
        verify(eventRepo, times(1)).save(any(LeadPipelineEvent.class));
        verify(mockCounter, atLeastOnce()).increment();
    }

    @Test
    public void testScanPipelineFailuresDetectsOrphanedWebhooks() {
        // Arrange
        List<LeadPipelineEvent> events = new ArrayList<>();
        
        LeadPipelineEvent webhookEvent = new LeadPipelineEvent();
        webhookEvent.setWorkspaceId("w-1");
        webhookEvent.setEventType("WEBHOOK_RECEIVED");
        webhookEvent.setStatus("SUCCESS");
        webhookEvent.setCreatedAt(LocalDateTime.now());
        events.add(webhookEvent);

        when(eventRepo.findAll()).thenReturn(events);
        when(alertRepo.findByWorkspaceIdAndResolvedOrderByCreatedAtDesc("w-1", false))
                .thenReturn(Collections.emptyList());

        // Act
        service.scanPipelineFailures();

        // Assert
        verify(alertRepo, times(1)).save(any(SystemAlert.class));
    }

    @Test
    public void testScanPipelineFailuresNoAlertIfLeadCreated() {
        // Arrange
        List<LeadPipelineEvent> events = new ArrayList<>();
        
        LeadPipelineEvent webhookEvent = new LeadPipelineEvent();
        webhookEvent.setWorkspaceId("w-1");
        webhookEvent.setEventType("WEBHOOK_RECEIVED");
        webhookEvent.setStatus("SUCCESS");
        webhookEvent.setCreatedAt(LocalDateTime.now());
        events.add(webhookEvent);

        LeadPipelineEvent leadEvent = new LeadPipelineEvent();
        leadEvent.setWorkspaceId("w-1");
        leadEvent.setEventType("LEAD_CREATED");
        leadEvent.setStatus("SUCCESS");
        leadEvent.setCreatedAt(LocalDateTime.now());
        events.add(leadEvent);

        when(eventRepo.findAll()).thenReturn(events);

        // Act
        service.scanPipelineFailures();

        // Assert
        verify(alertRepo, never()).save(any(SystemAlert.class));
    }

    @Test
    public void testScanPipelineFailuresAlertsOnMissingWhatsApp() {
        // Arrange
        List<LeadPipelineEvent> events = new ArrayList<>();
        
        LeadPipelineEvent leadEvent = new LeadPipelineEvent();
        leadEvent.setWorkspaceId("w-1");
        leadEvent.setLeadId("lead-999");
        leadEvent.setEventType("LEAD_CREATED");
        leadEvent.setStatus("SUCCESS");
        leadEvent.setCreatedAt(LocalDateTime.now());
        events.add(leadEvent);

        when(eventRepo.findAll()).thenReturn(events);
        when(alertRepo.findByWorkspaceIdAndResolvedOrderByCreatedAtDesc("w-1", false))
                .thenReturn(Collections.emptyList());

        // Act
        service.scanPipelineFailures();

        // Assert
        verify(alertRepo, times(1)).save(any(SystemAlert.class));
    }
}
