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
    @Mock private LocalApprovalSafetyService localApprovalSafetyService;

    private WhatsAppService service;
    private ObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        when(localApprovalSafetyService.shouldRequireApprovalOnly(anyString())).thenReturn(false);
        service = new WhatsAppService(
                messageRepo, templateRepo, configRepo, leadRepo, interactionRepo, alertService, mapper,
                leadPipelineTrackingService, localApprovalSafetyService
        );
    }

    @Test
    public void testSendLeadResponseBlockedInLocalModeBeforeExternalCall() {
        Lead lead = new Lead();
        lead.setId("lead-123");
        lead.setWorkspaceId("ws-123");
        lead.setPhone("919876543210");
        when(localApprovalSafetyService.shouldRequireApprovalOnly("WHATSAPP_SEND")).thenReturn(true);

        boolean sent = service.sendLeadResponse(lead, "followup_day1", List.of("DolphinAI"));

        assertFalse(sent);
        verify(localApprovalSafetyService).auditBlockedExecution(
                eq("ws-123"),
                eq("WHATSAPP_SEND"),
                eq("Lead"),
                eq("lead-123"),
                contains("Template send blocked")
        );
        verify(configRepo, never()).findByWorkspaceId(anyString());
        verify(messageRepo, never()).save(any());
        verify(leadPipelineTrackingService, never()).recordWhatsAppSent(any(), any(), any());
    }

    @Test
    public void testFollowUpSchedulerBlockedInLocalModeBeforeLeadMutation() {
        when(localApprovalSafetyService.shouldRequireApprovalOnly("WHATSAPP_FOLLOW_UP_SCHEDULER")).thenReturn(true);

        service.executeFollowUpSequences();

        verify(localApprovalSafetyService).auditBlockedExecution(
                isNull(),
                eq("WHATSAPP_FOLLOW_UP_SCHEDULER"),
                eq("Lead"),
                isNull(),
                contains("Scheduled WhatsApp follow-up cycle blocked")
        );
        verify(leadRepo, never()).findLeadsForFollowUp(any(LocalDateTime.class));
        verify(leadRepo, never()).save(any());
        verify(messageRepo, never()).save(any());
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
