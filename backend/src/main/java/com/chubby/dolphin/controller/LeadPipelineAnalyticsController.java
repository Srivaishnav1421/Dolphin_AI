package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.SystemAlert;
import com.chubby.dolphin.repository.LeadPipelineEventRepository;
import com.chubby.dolphin.repository.SystemAlertRepository;
import com.chubby.dolphin.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leads/pipeline-health")
public class LeadPipelineAnalyticsController {

    private final LeadPipelineEventRepository eventRepo;
    private final SystemAlertRepository alertRepo;
    private final SecurityUtils sec;

    public LeadPipelineAnalyticsController(LeadPipelineEventRepository eventRepo,
                                            SystemAlertRepository alertRepo,
                                            SecurityUtils sec) {
        this.eventRepo = eventRepo;
        this.alertRepo = alertRepo;
        this.sec = sec;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPipelineHealth() {
        String workspaceId = sec.currentAccountId();

        long totalEvents = eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WEBHOOK_RECEIVED") +
                eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WORKSPACE_RESOLVED") +
                eventRepo.countByWorkspaceIdAndEventType(workspaceId, "LEAD_CREATED") +
                eventRepo.countByWorkspaceIdAndEventType(workspaceId, "LEAD_SCORED") +
                eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WHATSAPP_SENT") +
                eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WHATSAPP_DELIVERED") +
                eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WHATSAPP_REPLIED");

        long webhooksReceived = eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WEBHOOK_RECEIVED");
        long workspacesResolved = eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WORKSPACE_RESOLVED");
        long leadsCreated = eventRepo.countByWorkspaceIdAndEventType(workspaceId, "LEAD_CREATED");
        long whatsappSent = eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WHATSAPP_SENT");
        long whatsappDelivered = eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WHATSAPP_DELIVERED");
        long repliesReceived = eventRepo.countByWorkspaceIdAndEventType(workspaceId, "WHATSAPP_REPLIED");
        long pipelineFailed = eventRepo.countByWorkspaceIdAndStatus(workspaceId, "FAILED");

        // Ratios computations
        double webhookSuccessRate = webhooksReceived == 0 ? 100.0 : ((double) workspacesResolved / webhooksReceived) * 100.0;
        double leadCreationRate = workspacesResolved == 0 ? 100.0 : ((double) leadsCreated / workspacesResolved) * 100.0;
        double whatsappDeliveryRate = whatsappSent == 0 ? 100.0 : ((double) whatsappDelivered / whatsappSent) * 100.0;
        double replyRate = whatsappDelivered == 0 ? 0.0 : ((double) repliesReceived / whatsappDelivered) * 100.0;
        double pipelineFailureRate = totalEvents == 0 ? 0.0 : ((double) pipelineFailed / totalEvents) * 100.0;

        List<SystemAlert> activeAlerts = alertRepo.findByWorkspaceIdAndResolvedOrderByCreatedAtDesc(workspaceId, false);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLeads", leadsCreated);
        stats.put("webhookSuccessRate", round(webhookSuccessRate));
        stats.put("leadCreationRate", round(leadCreationRate));
        stats.put("whatsappDeliveryRate", round(whatsappDeliveryRate));
        stats.put("replyRate", round(replyRate));
        stats.put("pipelineFailureRate", round(pipelineFailureRate));
        stats.put("activeAlerts", activeAlerts);

        return ResponseEntity.ok(stats);
    }

    private double round(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
