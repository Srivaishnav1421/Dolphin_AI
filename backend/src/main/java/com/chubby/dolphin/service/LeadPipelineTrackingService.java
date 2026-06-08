package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.LeadPipelineEvent;
import com.chubby.dolphin.entity.SystemAlert;
import com.chubby.dolphin.repository.LeadPipelineEventRepository;
import com.chubby.dolphin.repository.SystemAlertRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class LeadPipelineTrackingService {

    private final LeadPipelineEventRepository eventRepo;
    private final SystemAlertRepository alertRepo;
    private final MeterRegistry meterRegistry;

    // In-memory cache to store computed metrics for Micrometer Gauges
    private final ConcurrentHashMap<String, Double> metricsMap = new ConcurrentHashMap<>();

    public LeadPipelineTrackingService(LeadPipelineEventRepository eventRepo,
                                       SystemAlertRepository alertRepo,
                                       MeterRegistry meterRegistry) {
        this.eventRepo = eventRepo;
        this.alertRepo = alertRepo;
        this.meterRegistry = meterRegistry;

        // Initialize Gauges
        meterRegistry.gauge("lead_pipeline_success_rate", metricsMap, map -> map.getOrDefault("success_rate", 100.0));
        meterRegistry.gauge("whatsapp_delivery_rate", metricsMap, map -> map.getOrDefault("delivery_rate", 100.0));
    }

    public void recordWebhookReceived(String workspaceId, String details) {
        saveEvent(workspaceId, null, "WEBHOOK_RECEIVED", "SUCCESS", details);
    }

    public void recordWorkspaceResolved(String workspaceId, String leadId, String details) {
        saveEvent(workspaceId, leadId, "WORKSPACE_RESOLVED", "SUCCESS", details);
    }

    public void recordLeadCreated(String workspaceId, String leadId, String details) {
        saveEvent(workspaceId, leadId, "LEAD_CREATED", "SUCCESS", details);
    }

    public void recordLeadScored(String workspaceId, String leadId, String details) {
        saveEvent(workspaceId, leadId, "LEAD_SCORED", "SUCCESS", details);
    }

    public void recordWhatsAppSent(String workspaceId, String leadId, String details) {
        saveEvent(workspaceId, leadId, "WHATSAPP_SENT", "SUCCESS", details);
    }

    public void recordWhatsAppDelivered(String workspaceId, String leadId, String details) {
        saveEvent(workspaceId, leadId, "WHATSAPP_DELIVERED", "SUCCESS", details);
    }

    public void recordReplyReceived(String workspaceId, String leadId, String details) {
        saveEvent(workspaceId, leadId, "WHATSAPP_REPLIED", "SUCCESS", details);
    }

    public void recordFailure(String workspaceId, String leadId, String eventType, String details) {
        saveEvent(workspaceId, leadId, eventType, "FAILED", details);
        
        // Log custom metric failure counter
        meterRegistry.counter("lead_pipeline_failures_total", "workspaceId", workspaceId != null ? workspaceId : "unknown", "eventType", eventType).increment();
    }

    private void saveEvent(String workspaceId, String leadId, String eventType, String status, String details) {
        LeadPipelineEvent event = new LeadPipelineEvent();
        event.setWorkspaceId(workspaceId);
        event.setLeadId(leadId);
        event.setEventType(eventType);
        event.setStatus(status);
        event.setDetails(details);
        event.setCreatedAt(LocalDateTime.now());
        eventRepo.save(event);

        // Standard Micrometer metrics tracking
        meterRegistry.counter("lead_pipeline_events_total",
                "workspaceId", workspaceId != null ? workspaceId : "unknown",
                "eventType", eventType,
                "status", status).increment();

        log.debug("📊 Pipeline Event Recorded: {} | Status: {} | Tenant: {}", eventType, status, workspaceId);
    }

    /**
     * Periodically scan lead events for pipeline anomalies and raise appropriate SystemAlert warnings.
     * Runs every 5 minutes in a production cluster environment.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void scanPipelineFailures() {
        log.info("🔍 Running Pipeline Anomaly Detection Engine...");
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(15);
        List<LeadPipelineEvent> recentEvents = eventRepo.findAll();

        for (LeadPipelineEvent event : recentEvents) {
            if (event.getCreatedAt().isBefore(thresholdTime)) {
                continue;
            }

            // Anomaly 1: Webhook received but no workspace resolved or lead created
            if ("WEBHOOK_RECEIVED".equals(event.getEventType()) && "SUCCESS".equals(event.getStatus())) {
                boolean hasLeadCreated = recentEvents.stream().anyMatch(e ->
                        "LEAD_CREATED".equals(e.getEventType()) &&
                        event.getWorkspaceId() != null &&
                        event.getWorkspaceId().equals(e.getWorkspaceId())
                );
                if (!hasLeadCreated) {
                    raiseAlert(event.getWorkspaceId(), "WEBHOOK_ORPHANED",
                            "Webhook received but no lead was successfully created within the 15-minute pipeline window.",
                            "CRITICAL");
                }
            }

            // Anomaly 2: Lead created but no WhatsApp follow-up dispatched
            if ("LEAD_CREATED".equals(event.getEventType()) && event.getLeadId() != null) {
                boolean hasWhatsAppSent = recentEvents.stream().anyMatch(e ->
                        "WHATSAPP_SENT".equals(e.getEventType()) && event.getLeadId().equals(e.getLeadId())
                );
                if (!hasWhatsAppSent) {
                    raiseAlert(event.getWorkspaceId(), "WHATSAPP_DISPATCH_MISSING",
                            "Lead created [ID: " + event.getLeadId() + "] but no follow-up WhatsApp template was dispatched.",
                            "HIGH");
                }
            }

            // Anomaly 3: WhatsApp sent but no delivery receipt within timeframe
            if ("WHATSAPP_SENT".equals(event.getEventType()) && event.getLeadId() != null) {
                boolean hasWhatsAppDelivered = recentEvents.stream().anyMatch(e ->
                        "WHATSAPP_DELIVERED".equals(e.getEventType()) && event.getLeadId().equals(e.getLeadId())
                );
                if (!hasWhatsAppDelivered) {
                    raiseAlert(event.getWorkspaceId(), "WHATSAPP_DELIVERY_SILENCE",
                            "WhatsApp follow-up was dispatched to lead [ID: " + event.getLeadId() + "] but no delivery receipt was recorded.",
                            "MEDIUM");
                }
            }
        }

        // 4. Compute rates for Prometheus Gauges
        computeObservabilityRates();
    }

    private void raiseAlert(String workspaceId, String alertType, String message, String severity) {
        // Prevent duplicate unresolved alerts of the same type for the workspace
        List<SystemAlert> activeAlerts = alertRepo.findByWorkspaceIdAndResolvedOrderByCreatedAtDesc(workspaceId, false);
        boolean exists = activeAlerts.stream().anyMatch(a -> alertType.equals(a.getAlertType()));
        
        if (!exists) {
            SystemAlert alert = new SystemAlert();
            alert.setWorkspaceId(workspaceId);
            alert.setAlertType(alertType);
            alert.setMessage(message);
            alert.setSeverity(severity);
            alert.setResolved(false);
            alert.setCreatedAt(LocalDateTime.now());
            alertRepo.save(alert);

            log.warn("🚨 [SYSTEM ALERT raised] Type: {} | Severity: {} | Workspace: {} | Msg: {}",
                    alertType, severity, workspaceId, message);
        }
    }

    /**
     * Compute real-time pipeline KPIs to update Micrometer Gauges
     */
    public void computeObservabilityRates() {
        long totalEvents = eventRepo.count();
        if (totalEvents == 0) {
            metricsMap.put("success_rate", 100.0);
            metricsMap.put("delivery_rate", 100.0);
            return;
        }

        long failedEvents = eventRepo.findAll().stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        double successRate = ((double)(totalEvents - failedEvents) / totalEvents) * 100.0;
        metricsMap.put("success_rate", successRate);

        // WhatsApp Delivery Rate
        long totalSent = eventRepo.findAll().stream().filter(e -> "WHATSAPP_SENT".equals(e.getEventType())).count();
        long totalDelivered = eventRepo.findAll().stream().filter(e -> "WHATSAPP_DELIVERED".equals(e.getEventType())).count();
        
        double deliveryRate = totalSent == 0 ? 100.0 : ((double) totalDelivered / totalSent) * 100.0;
        metricsMap.put("delivery_rate", deliveryRate);

        // Raise alerts based on aggregate thresholds
        if (successRate < 95.0) {
            raiseAlert("SYSTEM", "PIPELINE_SUCCESS_RATE_LOW",
                    "Global Lead Pipeline success rate drops to " + String.format("%.1f", successRate) + "% (threshold < 95%).",
                    "HIGH");
        }
        if (deliveryRate < 90.0) {
            raiseAlert("SYSTEM", "WHATSAPP_DELIVERY_RATE_LOW",
                    "Global WhatsApp follow-up delivery rate drops to " + String.format("%.1f", deliveryRate) + "% (threshold < 90%).",
                    "HIGH");
        }
    }
}
