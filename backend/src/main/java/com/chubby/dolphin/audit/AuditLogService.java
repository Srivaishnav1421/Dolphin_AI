package com.chubby.dolphin.audit;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.security.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(User actor,
                       Organization organization,
                       String workspaceId,
                       String action,
                       String entityType,
                       String entityId,
                       String details) {
        AuditLog audit = new AuditLog();
        audit.setUserEmail(actor != null ? actor.getEmail() : currentEmail());
        audit.setActorId(actor != null ? actor.getId() : "system");
        audit.setActorType(actor != null ? "USER" : "SYSTEM");
        audit.setAction(action);
        audit.setEventType(action);
        audit.setResourceType(entityType);
        audit.setResourceId(entityId);
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setDetails(details);
        audit.setOrganizationId(organization != null ? organization.getId() : null);
        audit.setWorkspaceId(workspaceId != null && !workspaceId.isBlank() ? workspaceId : TenantContext.getCurrentTenant());
        audit.setCorrelationId(UUID.randomUUID().toString());
        audit.setTimestamp(LocalDateTime.now());
        audit.setIpAddress("system-local");
        auditLogRepository.save(audit);
    }

    public void recordAuthEvent(User actor,
                                String email,
                                String organizationId,
                                String workspaceId,
                                String action,
                                boolean success,
                                String ipAddress,
                                String userAgent,
                                String details) {
        AuditLog audit = new AuditLog();
        audit.setUserEmail(email != null ? email : currentEmail());
        audit.setActorId(actor != null ? actor.getId() : "unknown");
        audit.setActorType(actor != null ? "USER" : "AUTH_SUBJECT");
        audit.setAction(action);
        audit.setEventType(action);
        audit.setResourceType("AUTH");
        audit.setResourceId(actor != null ? actor.getId() : sanitizeIdentifier(email));
        audit.setEntityType("AUTH");
        audit.setEntityId(actor != null ? actor.getId() : sanitizeIdentifier(email));
        audit.setOrganizationId(organizationId);
        audit.setWorkspaceId(workspaceId);
        audit.setIpAddress(safeValue(ipAddress, 120));
        audit.setCorrelationId(UUID.randomUUID().toString());
        audit.setTimestamp(LocalDateTime.now());
        audit.setDetails(redact("success=" + success
                + "; details=" + safeValue(details, 400)
                + "; userAgent=" + safeValue(userAgent, 240)));
        auditLogRepository.save(audit);
    }

    public String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String redacted = value;
        String[] sensitiveKeys = {
                "password", "token", "secret", "apikey", "api_key", "refreshToken",
                "accessToken", "authorization", "credential", "webhookSecret"
        };
        for (String key : sensitiveKeys) {
            redacted = redacted.replaceAll("(?i)(" + key + "\\s*[=:]\\s*)[^;,\\s}]+", "$1[REDACTED]");
        }
        return safeValue(redacted, 1000);
    }

    private String safeValue(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String sanitizeIdentifier(String value) {
        String safe = safeValue(value, 256);
        return safe.isBlank() ? "unknown" : safe.toLowerCase(Locale.ROOT);
    }

    private String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        return "system-daemon";
    }
}
