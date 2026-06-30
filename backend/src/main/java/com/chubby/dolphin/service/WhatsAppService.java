package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.*;
import com.chubby.dolphin.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@Transactional
public class WhatsAppService {

    private final WhatsAppMessageRepository messageRepo;
    private final WhatsAppTemplateRepository templateRepo;
    private final WorkspaceConfigRepository configRepo;
    private final LeadRepository leadRepo;
    private final LeadInteractionRepository interactionRepo;
    private final AlertService alertService;
    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final LeadPipelineTrackingService leadPipelineTrackingService;
    private final LocalApprovalSafetyService localApprovalSafetyService;

    public WhatsAppService(WhatsAppMessageRepository messageRepo,
                             WhatsAppTemplateRepository templateRepo,
                             WorkspaceConfigRepository configRepo,
                             LeadRepository leadRepo,
                             LeadInteractionRepository interactionRepo,
                             AlertService alertService,
                             ObjectMapper mapper,
                             LeadPipelineTrackingService leadPipelineTrackingService) {
        this(messageRepo, templateRepo, configRepo, leadRepo, interactionRepo, alertService, mapper,
                leadPipelineTrackingService, null);
    }

    @Autowired
    public WhatsAppService(WhatsAppMessageRepository messageRepo,
                             WhatsAppTemplateRepository templateRepo,
                             WorkspaceConfigRepository configRepo,
                             LeadRepository leadRepo,
                             LeadInteractionRepository interactionRepo,
                             AlertService alertService,
                             ObjectMapper mapper,
                             LeadPipelineTrackingService leadPipelineTrackingService,
                             LocalApprovalSafetyService localApprovalSafetyService) {
        this.messageRepo = messageRepo;
        this.templateRepo = templateRepo;
        this.configRepo = configRepo;
        this.leadRepo = leadRepo;
        this.interactionRepo = interactionRepo;
        this.alertService = alertService;
        this.mapper = mapper;
        this.leadPipelineTrackingService = leadPipelineTrackingService;
        this.localApprovalSafetyService = localApprovalSafetyService;
        this.webClient = WebClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Sends an autonomous template-based WhatsApp message using Meta Cloud API.
     */
    public boolean sendLeadResponse(Lead lead, String templateName, List<String> params) {
        log.info("📞 Initiating auto-WhatsApp response for Lead: {} via template: {}", lead.getId(), templateName);

        String workspaceId = lead.getWorkspaceId() != null ? lead.getWorkspaceId() : lead.getAccountId();
        if (localSafetyBlocks("WHATSAPP_SEND")) {
            localApprovalSafetyService.auditBlockedExecution(
                    workspaceId,
                    "WHATSAPP_SEND",
                    "Lead",
                    lead.getId(),
                    "Template send blocked before Meta Cloud API call; template=" + templateName
            );
            return false;
        }
        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId).orElse(null);

        if (config == null || config.getWhatsappPhoneId() == null || config.getWhatsappToken() == null) {
            log.warn("WhatsApp credentials not configured for workspace {}, aborting dispatch.", workspaceId);
            return false;
        }

        // Prepare request body according to Meta Cloud API requirements
        List<Map<String, Object>> bodyParameters = new ArrayList<>();
        if (params != null) {
            for (String param : params) {
                bodyParameters.add(Map.of("type", "text", "text", param));
            }
        }

        Map<String, Object> component = Map.of(
                "type", "body",
                "parameters", bodyParameters
        );

        Map<String, Object> template = Map.of(
                "name", templateName,
                "language", Map.of("code", "en_IN"),
                "components", List.of(component)
        );

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", lead.getPhone() != null ? lead.getPhone() : "",
                "type", "template",
                "template", template
        );

        WhatsAppMessage message = new WhatsAppMessage();
        message.setWorkspaceId(workspaceId);
        message.setLeadId(lead.getId());
        message.setToNumber(lead.getPhone() != null ? lead.getPhone() : "");
        message.setTemplateName(templateName);
        try {
            message.setTemplateParams(mapper.writeValueAsString(params));
        } catch (Exception e) {
            message.setTemplateParams("[]");
        }
        message.setSentAt(LocalDateTime.now());

        try {
            String url = "https://graph.facebook.com/v21.0/" + config.getWhatsappPhoneId() + "/messages";
            String responseStr = webClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(config.getWhatsappToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = mapper.readTree(responseStr);
            String messageId = root.path("messages").get(0).path("id").asText();

            message.setMessageId(messageId);
            message.setStatus("SENT");
            messageRepo.save(message);
            log.info("✅ WhatsApp successfully sent to {} with messageId: {}", lead.getPhone(), messageId);
            leadPipelineTrackingService.recordWhatsAppSent(workspaceId, lead.getId(), "WhatsApp follow-up template " + templateName + " successfully sent.");
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to send WhatsApp message: {}", e.getMessage());
            message.setStatus("FAILED");
            message.setErrorMessage(e.getMessage());
            messageRepo.save(message);
            leadPipelineTrackingService.recordFailure(workspaceId, lead.getId(), "WHATSAPP_SENT", "Failed to send WhatsApp message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Processes incoming webhook reply payload from Meta Cloud API.
     */
    public void receiveWebhook(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode value = root.path("entry").get(0).path("changes").get(0).path("value");

            // Look up message status updates
            JsonNode statuses = value.path("statuses");
            if (statuses.isArray() && !statuses.isEmpty()) {
                JsonNode statusNode = statuses.get(0);
                String msgId = statusNode.path("id").asText();
                String statusStr = statusNode.path("status").asText().toUpperCase(); // DELIVERED, READ, etc.

                WhatsAppMessage msg = messageRepo.findByMessageId(msgId).orElse(null);
                if (msg != null) {
                    msg.setStatus(statusStr);
                    if ("DELIVERED".equals(statusStr)) {
                        msg.setDeliveredAt(LocalDateTime.now());
                        leadPipelineTrackingService.recordWhatsAppDelivered(msg.getWorkspaceId(), msg.getLeadId(), "WhatsApp message delivery confirmed for messageId: " + msgId);
                    }
                    if ("READ".equals(statusStr)) msg.setReadAt(LocalDateTime.now());
                    messageRepo.save(msg);
                    log.info("🔔 WhatsApp delivery status updated: {} -> {}", msgId, statusStr);
                }
                return;
            }

            // Look up message body reply
            JsonNode messages = value.path("messages");
            if (messages.isArray() && !messages.isEmpty()) {
                JsonNode msgNode = messages.get(0);
                String fromPhone = msgNode.path("from").asText();
                String textBody = msgNode.path("text").path("body").asText().trim();

                // Look up matching Lead by phone number
                Lead lead = leadRepo.findFirstByPhoneOrderByCreatedAtDesc(fromPhone).orElse(null);
                if (lead != null) {
                    lead.setLastReply(textBody);
                    lead.setLastReplyAt(LocalDateTime.now());

                    leadPipelineTrackingService.recordReplyReceived(lead.getWorkspaceId() != null ? lead.getWorkspaceId() : lead.getAccountId(), lead.getId(), "WhatsApp customer reply: " + textBody);

                    if ("STOP".equalsIgnoreCase(textBody)) {
                        lead.setOptedOut(true);
                        log.info("Opt-out received for lead: {}, unsubscribe action triggered.", lead.getId());
                    } else if (List.of("YES", "DETAILS", "CALL").contains(textBody.toUpperCase())) {
                        LeadInteraction interaction = new LeadInteraction();
                        interaction.setLeadId(lead.getId());
                        interaction.setWorkspaceId(lead.getWorkspaceId() != null ? lead.getWorkspaceId() : lead.getAccountId());
                        interaction.setType("POSITIVE_REPLY");
                        interaction.setDetails("User replied: " + textBody);
                        interactionRepo.save(interaction);

                        // Trigger notification
                        alertService.notifyReportReady(
                                lead.getWorkspaceId() != null ? lead.getWorkspaceId() : lead.getAccountId(),
                                "HOT Engagement Interaction for Lead: " + lead.getName(),
                                "Lead " + lead.getName() + " replied positively on WhatsApp.",
                                "Reply content: " + textBody
                        );
                    }
                    leadRepo.save(lead);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse WhatsApp Webhook payload: {}", e.getMessage());
            leadPipelineTrackingService.recordFailure(null, null, "WHATSAPP_REPLIED", "Failed to parse WhatsApp Webhook payload: " + e.getMessage());
        }
    }

    /**
     * Daily scheduled task (10 AM) to follow-up on quiet leads.
     */
    @Scheduled(cron = "0 0 10 * * *")
    public void executeFollowUpSequences() {
        log.info("⏰ Running Scheduled WhatsApp Follow-up cycle...");
        if (localSafetyBlocks("WHATSAPP_FOLLOW_UP_SCHEDULER")) {
            localApprovalSafetyService.auditBlockedExecution(
                    null,
                    "WHATSAPP_FOLLOW_UP_SCHEDULER",
                    "Lead",
                    null,
                    "Scheduled WhatsApp follow-up cycle blocked before lead/message mutations."
            );
            return;
        }
        LocalDateTime targetTime = LocalDateTime.now().minusHours(24);

        // Find leads created before 24h that haven't opted out and haven't replied
        List<Lead> leads = leadRepo.findLeadsForFollowUp(targetTime);

        for (Lead lead : leads) {
            if (Boolean.TRUE.equals(lead.getOptedOut())) continue;

            // Check how many messages have already been sent to count the day sequence
            List<WhatsAppMessage> history = messageRepo.findByLeadId(lead.getId());
            long count = history.stream().filter(m -> "SENT".equals(m.getStatus()) || "DELIVERED".equals(m.getStatus())).count();

            if (count == 1) {
                // Day 1 Follow-up
                sendLeadResponse(lead, "followup_day1", List.of(lead.getName(), "our exclusive program"));
            } else if (count == 2) {
                // Day 3 Follow-up
                sendLeadResponse(lead, "followup_day3", List.of(lead.getName(), "our offering"));
            } else if (count == 3) {
                // Day 7: Mark cold and alert workspace owner
                lead.setStatus("COLD");
                leadRepo.save(lead);
                alertService.notifyReportReady(
                        lead.getWorkspaceId() != null ? lead.getWorkspaceId() : lead.getAccountId(),
                        "Lead Status Warning - COLD: " + lead.getName(),
                        "Lead has not responded to WhatsApp follow-ups.",
                        "Final status changed to COLD."
                );
            }
        }
    }

    private boolean localSafetyBlocks(String action) {
        return localApprovalSafetyService != null && localApprovalSafetyService.shouldRequireApprovalOnly(action);
    }
}
