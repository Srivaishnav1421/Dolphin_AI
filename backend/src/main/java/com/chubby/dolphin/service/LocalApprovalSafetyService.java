package com.chubby.dolphin.service;

import com.chubby.dolphin.audit.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;

@Service
@Slf4j
public class LocalApprovalSafetyService {

    public static final String AUDIT_ACTION_BLOCKED = "LOCAL_SAFETY_BLOCKED";

    private final Environment environment;
    private final AuditLogService auditLogService;
    private final boolean localModeEnabled;

    public LocalApprovalSafetyService(Environment environment,
                                      AuditLogService auditLogService,
                                      @Value("${dolphin.local-mode.enabled:false}") boolean localModeEnabled) {
        this.environment = environment;
        this.auditLogService = auditLogService;
        this.localModeEnabled = localModeEnabled;
    }

    public boolean isLocalModeEnabled() {
        return localModeEnabled;
    }

    public boolean isLocalOrDevProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("dev") || profile.equals("local"));
    }

    public boolean isApprovalFirstLocalMode() {
        return isLocalModeEnabled() || isLocalOrDevProfileActive();
    }

    public boolean isExternalExecutionAllowed() {
        return !isApprovalFirstLocalMode();
    }

    public boolean shouldRequireApprovalOnly(String action) {
        return !isExternalExecutionAllowed();
    }

    public String blockedMessage(String action) {
        return cleanAction(action) + " is disabled in local approval-first mode. No external execution was performed.";
    }

    public void auditBlockedExecution(String workspaceId,
                                      String action,
                                      String entityType,
                                      String entityId,
                                      String details) {
        String cleanAction = cleanAction(action);
        String cleanDetails = "action=" + cleanAction
                + "; localModeEnabled=" + isLocalModeEnabled()
                + "; localOrDevProfileActive=" + isLocalOrDevProfileActive()
                + "; externalExecutionAllowed=false"
                + "; details=" + (details == null ? "" : details);
        log.warn("Local approval-first safety blocked action={} entityType={} entityId={}",
                cleanAction, entityType, entityId);
        auditLogService.record(
                null,
                null,
                workspaceId,
                AUDIT_ACTION_BLOCKED,
                entityType == null || entityType.isBlank() ? "ExternalExecution" : entityType,
                entityId,
                auditLogService.redact(cleanDetails)
        );
    }

    private String cleanAction(String action) {
        if (action == null || action.isBlank()) {
            return "Risky execution";
        }
        return action.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
    }
}
