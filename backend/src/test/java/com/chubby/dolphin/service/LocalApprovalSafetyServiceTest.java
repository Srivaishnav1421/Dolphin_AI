package com.chubby.dolphin.service;

import com.chubby.dolphin.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalApprovalSafetyServiceTest {

    @Test
    void localModeBlocksExternalExecutionAndRequiresApprovalOnly() {
        Environment environment = mock(Environment.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        LocalApprovalSafetyService service = new LocalApprovalSafetyService(environment, auditLogService, true);

        assertTrue(service.isLocalModeEnabled());
        assertFalse(service.isExternalExecutionAllowed());
        assertTrue(service.shouldRequireApprovalOnly("META_LAUNCH_AD"));
        assertTrue(service.blockedMessage("Meta launch").contains("local approval-first mode"));
    }

    @Test
    void devProfileBlocksExternalExecutionEvenWhenLocalModeFlagIsFalse() {
        Environment environment = mock(Environment.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        LocalApprovalSafetyService service = new LocalApprovalSafetyService(environment, auditLogService, false);

        assertTrue(service.isLocalOrDevProfileActive());
        assertFalse(service.isExternalExecutionAllowed());
        assertTrue(service.shouldRequireApprovalOnly("WHATSAPP_SEND"));
    }

    @Test
    void blockedExecutionWritesAuditLog() {
        Environment environment = mock(Environment.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(auditLogService.redact(anyString())).thenAnswer(inv -> inv.getArgument(0));

        LocalApprovalSafetyService service = new LocalApprovalSafetyService(environment, auditLogService, false);

        service.auditBlockedExecution("ws-1", "META_LAUNCH_AD", "MetaAd", "meta-1", "token=secret");

        verify(auditLogService).record(
                isNull(),
                isNull(),
                eq("ws-1"),
                eq(LocalApprovalSafetyService.AUDIT_ACTION_BLOCKED),
                eq("MetaAd"),
                eq("meta-1"),
                contains("externalExecutionAllowed=false")
        );
    }
}
