package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.BrainEvent;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.repository.BrainEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant-scoped action trail for DolphinAI's assistant, automation, and controlled autonomous modes.
 * This intentionally stores business-readable facts and avoids exposing model/provider/token details.
 */
@Service
@Slf4j
public class BrainActionAuditService {

    private final AuditLogRepository auditLogRepo;
    private final BrainEventRepository brainEventRepo;

    public BrainActionAuditService(AuditLogRepository auditLogRepo, BrainEventRepository brainEventRepo) {
        this.auditLogRepo = auditLogRepo;
        this.brainEventRepo = brainEventRepo;
    }

    public AuditLog logAiAction(String workspaceId,
                                String mode,
                                String action,
                                String entityType,
                                String entityId,
                                String reason,
                                String trigger,
                                String status) {
        AuditLog audit = new AuditLog();
        audit.setWorkspaceId(workspaceId);
        audit.setUserEmail("dolphin-ai");
        audit.setActorId("dolphin-ai");
        audit.setActorType("AI_BRAIN");
        audit.setAction(normalizeAction(action));
        audit.setEventType("AI_ACTION_" + safe(status, "LOGGED"));
        audit.setResourceType(entityType);
        audit.setResourceId(entityId);
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setCorrelationId(UUID.randomUUID().toString());
        audit.setIpAddress("system-local");
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails(buildDetails(mode, action, reason, trigger, status));
        AuditLog saved = auditLogRepo.save(audit);

        BrainEvent event = new BrainEvent();
        event.setWorkspaceId(workspaceId);
        event.setEventType("AI_ACTION_" + safe(status, "LOGGED"));
        event.setSeverity("FAILED".equalsIgnoreCase(status) ? "WARNING" : "INFO");
        event.setMessage(businessMessage(action, entityType, status));
        event.setCreatedAt(LocalDateTime.now());
        brainEventRepo.save(event);

        log.info("AI action audited: workspace={}, action={}, entity={}, status={}", workspaceId, action, entityId, status);
        return saved;
    }

    public List<AuditLog> recentAiActions(String workspaceId) {
        return auditLogRepo.findTop100ByWorkspaceIdAndActorTypeOrderByTimestampDesc(workspaceId, "AI_BRAIN");
    }

    private String buildDetails(String mode, String action, String reason, String trigger, String status) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                    "mode", safe(mode, "CONTROLLED_AUTONOMOUS"),
                    "action", safe(action, "AI_ACTION"),
                    "reason", safe(reason, "DolphinAI identified a business improvement opportunity."),
                    "trigger", safe(trigger, "SYSTEM_RULE"),
                    "status", safe(status, "LOGGED")
            ));
        } catch (Exception e) {
            return "AI action logged: " + safe(action, "AI_ACTION") + " | " + safe(status, "LOGGED");
        }
    }

    private String businessMessage(String action, String entityType, String status) {
        String cleanAction = safe(action, "AI action").replace('_', ' ').toLowerCase();
        String cleanEntity = safe(entityType, "business record").replace('_', ' ').toLowerCase();
        return "DolphinAI " + cleanAction + " for " + cleanEntity + " (" + safe(status, "logged").toLowerCase() + ").";
    }

    private String normalizeAction(String action) {
        return safe(action, "AI_ACTION").toUpperCase().replace(' ', '_');
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
