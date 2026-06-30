package com.chubby.dolphin.approval;

import com.chubby.dolphin.approval.dto.ApprovalItemCreateRequest;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovalQueueServiceTest {

    private ApprovalItemRepository repository;
    private AccessControlService access;
    private AuditLogService auditLogService;
    private ApplicationEventPublisher eventPublisher;
    private ApprovalQueueService service;
    private UUID workspaceId;
    private UUID userId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        repository = mock(ApprovalItemRepository.class);
        access = mock(AccessControlService.class);
        auditLogService = mock(AuditLogService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ApprovalQueueService(repository, access, auditLogService, eventPublisher);

        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        Organization org = new Organization();
        org.setId(orgId.toString());
        User user = new User();
        user.setId(userId.toString());
        user.setEmail("owner@test.local");
        user.setRole("OWNER");
        user.setOrganization(org);

        when(access.currentWorkspaceId()).thenReturn(workspaceId.toString());
        when(access.currentUser()).thenReturn(user);
        when(auditLogService.redact(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createApprovalItemPersistsDbRowAndAudits() {
        when(repository.save(any(ApprovalItem.class))).thenAnswer(inv -> {
            ApprovalItem item = inv.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        var response = service.createApprovalItem(new ApprovalItemCreateRequest(
                null,
                null,
                null,
                ApprovalSourceModule.CAMPAIGN,
                "Campaign",
                UUID.randomUUID().toString(),
                ApprovalActionType.PAUSE_CAMPAIGN,
                "Pause campaign",
                "Pause after spend threshold",
                "{\"reason\":\"threshold\"}",
                "{\"spend\":600}",
                ApprovalSeverity.HIGH,
                true,
                null
        ));

        assertNotNull(response.id());
        assertEquals(workspaceId.toString(), response.workspaceId());
        assertEquals(ApprovalStatus.PENDING, response.status());
        verify(repository).save(any(ApprovalItem.class));
        verify(auditLogService).record(any(), any(), eq(workspaceId.toString()),
                eq("APPROVAL_ITEM_CREATED"), eq("ApprovalItem"), anyString(), anyString());
    }

    @Test
    void salesCloserFollowUpApprovalIsPendingAndDoesNotRequireExecution() {
        when(repository.save(any(ApprovalItem.class))).thenAnswer(inv -> {
            ApprovalItem item = inv.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        var response = service.createApprovalItem(new ApprovalItemCreateRequest(
                null,
                null,
                null,
                ApprovalSourceModule.SALES_CLOSER,
                "Lead",
                UUID.randomUUID().toString(),
                ApprovalActionType.FOLLOW_UP,
                "Sales follow-up",
                "Send follow-up message",
                "{\"leadId\":\"lead-1\",\"sendsAutomatically\":false}",
                "{\"score\":65}",
                ApprovalSeverity.MEDIUM,
                false,
                null
        ));

        assertEquals(ApprovalSourceModule.SALES_CLOSER, response.sourceModule());
        assertEquals(ApprovalActionType.FOLLOW_UP, response.actionType());
        assertEquals(ApprovalStatus.PENDING, response.status());
        assertFalse(response.requiresExecution());
        verify(repository).save(argThat(item ->
                item.getSourceModule() == ApprovalSourceModule.SALES_CLOSER
                        && item.getActionType() == ApprovalActionType.FOLLOW_UP
                        && Boolean.FALSE.equals(item.getRequiresExecution())
        ));
    }

    @Test
    void pendingApprovalsReturnsOnlyCurrentWorkspace() {
        ApprovalItem item = item(ApprovalStatus.PENDING);
        when(repository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, ApprovalStatus.PENDING))
                .thenReturn(List.of(item));

        var results = service.listPendingApprovals();

        assertEquals(1, results.size());
        assertEquals(item.getId().toString(), results.get(0).id());
        verify(repository).findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, ApprovalStatus.PENDING);
    }

    @Test
    void emptyDbReturnsEmptyList() {
        when(repository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, ApprovalStatus.PENDING))
                .thenReturn(List.of());

        assertTrue(service.listPendingApprovals().isEmpty());
    }

    @Test
    void approveChangesPendingToApprovedAndAudits() {
        ApprovalItem item = item(ApprovalStatus.PENDING);
        when(repository.findByIdAndWorkspaceId(item.getId(), workspaceId)).thenReturn(Optional.of(item));
        when(repository.save(any(ApprovalItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.approveApprovalItem(item.getId().toString());

        assertEquals(ApprovalStatus.APPROVED, response.status());
        assertNotNull(response.approvedAt());
        verify(auditLogService).record(any(), any(), eq(workspaceId.toString()),
                eq("APPROVAL_ITEM_APPROVED"), eq("ApprovalItem"), eq(item.getId().toString()), anyString());
        verify(eventPublisher).publishEvent(any(ApprovalStatusChangedEvent.class));
    }

    @Test
    void rejectChangesPendingToRejectedAndAudits() {
        ApprovalItem item = item(ApprovalStatus.PENDING);
        when(repository.findByIdAndWorkspaceId(item.getId(), workspaceId)).thenReturn(Optional.of(item));
        when(repository.save(any(ApprovalItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.rejectApprovalItem(item.getId().toString(), "Not safe");

        assertEquals(ApprovalStatus.REJECTED, response.status());
        assertEquals("Not safe", response.rejectionReason());
        verify(auditLogService).record(any(), any(), eq(workspaceId.toString()),
                eq("APPROVAL_ITEM_REJECTED"), eq("ApprovalItem"), eq(item.getId().toString()), anyString());
        verify(eventPublisher).publishEvent(any(ApprovalStatusChangedEvent.class));
    }

    @Test
    void executeRejectedItemIsBlocked() {
        ApprovalItem item = item(ApprovalStatus.REJECTED);
        when(repository.findByIdAndWorkspaceId(item.getId(), workspaceId)).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class, () -> service.executeApprovalItem(item.getId().toString()));
    }

    @Test
    void executePendingItemIsBlocked() {
        ApprovalItem item = item(ApprovalStatus.PENDING);
        when(repository.findByIdAndWorkspaceId(item.getId(), workspaceId)).thenReturn(Optional.of(item));

        assertThrows(IllegalStateException.class, () -> service.executeApprovalItem(item.getId().toString()));
    }

    @Test
    void executeApprovedItemDoesNotMarkExecutedWithoutIntegration() {
        ApprovalItem item = item(ApprovalStatus.APPROVED);
        when(repository.findByIdAndWorkspaceId(item.getId(), workspaceId)).thenReturn(Optional.of(item));
        when(repository.save(any(ApprovalItem.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.executeApprovalItem(item.getId().toString());

        assertEquals(ApprovalStatus.APPROVED, response.approval().status());
        assertEquals("NOT_CONNECTED", response.approval().executionStatus());
        assertEquals("Approved, execution integration not connected yet.", response.message());
        verify(auditLogService).record(any(), any(), eq(workspaceId.toString()),
                eq("APPROVAL_EXECUTION_ATTEMPTED"), eq("ApprovalItem"), eq(item.getId().toString()), anyString());
        verify(auditLogService).record(any(), any(), eq(workspaceId.toString()),
                eq("APPROVAL_EXECUTION_FAILED"), eq("ApprovalItem"), eq(item.getId().toString()), anyString());
    }

    @Test
    void crossWorkspaceIdTamperingReturnsNotFound() {
        UUID approvalId = UUID.randomUUID();
        when(repository.findByIdAndWorkspaceId(approvalId, workspaceId)).thenReturn(Optional.empty());

        assertThrows(ApprovalQueueService.ApprovalNotFoundException.class,
                () -> service.getApprovalById(approvalId.toString()));
    }

    @Test
    void responseDoesNotExposeSecretsBeyondSafeFields() {
        ApprovalItem item = item(ApprovalStatus.PENDING);
        item.setRecommendationJson("{\"token\":\"secret-value\"}");
        when(repository.findByIdAndWorkspaceId(item.getId(), workspaceId)).thenReturn(Optional.of(item));

        var response = service.getApprovalById(item.getId().toString());

        assertEquals(item.getId().toString(), response.id());
        assertTrue(response.recommendationJson().contains("token"));
        assertFalse(response.recommendationJson().contains("secret-value"));
        assertTrue(response.recommendationJson().contains("[REDACTED]"));
    }

    private ApprovalItem item(ApprovalStatus status) {
        ApprovalItem item = new ApprovalItem();
        item.setId(UUID.randomUUID());
        item.setWorkspaceId(workspaceId);
        item.setAccountId(workspaceId);
        item.setOrganizationId(orgId);
        item.setSourceModule(ApprovalSourceModule.CAMPAIGN);
        item.setActionType(ApprovalActionType.PAUSE_CAMPAIGN);
        item.setTitle("Pause campaign");
        item.setDescription("Review before pausing");
        item.setSeverity(ApprovalSeverity.HIGH);
        item.setStatus(status);
        item.setRequiresExecution(true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return item;
    }
}
