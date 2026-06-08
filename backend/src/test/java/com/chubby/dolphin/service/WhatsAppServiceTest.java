package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WhatsAppServiceTest {

    @Mock private WhatsAppMessageRepository messageRepo;
    @Mock private WhatsAppTemplateRepository templateRepo;
    @Mock private WorkspaceConfigRepository configRepo;
    @Mock private LeadRepository leadRepo;
    @Mock private LeadInteractionRepository interactionRepo;
    @Mock private AlertService alertService;
    @Mock private LeadPipelineTrackingService leadPipelineTrackingService;

    private WhatsAppService service;
    private ObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        service = new WhatsAppService(
                messageRepo, templateRepo, configRepo, leadRepo, interactionRepo, alertService, mapper, leadPipelineTrackingService
        );
    }

    @Test
    public void testReceiveWebhook_StopOptOut() {
        String webhookPayload = """
                {
                  "entry": [
                    {
                      "changes": [
                        {
                          "value": {
                            "messages": [
                              {
                                "from": "919876543210",
                                "text": {
                                  "body": "STOP"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        Lead lead = new Lead();
        lead.setId("lead-123");
        lead.setPhone("919876543210");
        lead.setOptedOut(false);

        when(leadRepo.findFirstByPhoneOrderByCreatedAtDesc("919876543210"))
                .thenReturn(Optional.of(lead));

        service.receiveWebhook(webhookPayload);

        assertTrue(lead.getOptedOut());
        verify(leadRepo, times(1)).save(lead);
    }

    @Test
    public void testReceiveWebhook_PositiveEngagement() {
        String webhookPayload = """
                {
                  "entry": [
                    {
                      "changes": [
                        {
                          "value": {
                            "messages": [
                              {
                                "from": "919876543210",
                                "text": {
                                  "body": "YES"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        Lead lead = new Lead();
        lead.setId("lead-123");
        lead.setPhone("919876543210");
        lead.setAccountId("acc-789");

        when(leadRepo.findFirstByPhoneOrderByCreatedAtDesc("919876543210"))
                .thenReturn(Optional.of(lead));

        service.receiveWebhook(webhookPayload);

        verify(interactionRepo, times(1)).save(argThat(i -> "POSITIVE_REPLY".equals(i.getType())));
        verify(alertService, times(1)).notifyReportReady(any(), any(), any(), any());
    }
}
