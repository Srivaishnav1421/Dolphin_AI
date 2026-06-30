package com.chubby.dolphin.approval;

import com.chubby.dolphin.approval.dto.ApprovalItemResponse;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.TenantAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ApprovalQueueControllerTest {

    private ApprovalQueueService service;
    private AccessControlService access;
    private ApprovalQueueController controller;

    @BeforeEach
    void setUp() {
        service = mock(ApprovalQueueService.class);
        access = mock(AccessControlService.class);
        controller = new ApprovalQueueController(service, access);
    }

    @Test
    void listPendingRequiresReadPermission() {
        when(service.listPendingApprovals()).thenReturn(List.of(response(ApprovalStatus.PENDING)));

        var result = controller.pending();

        assertEquals(200, result.getStatusCode().value());
        verify(access).requireWorkspacePermission(Permission.APPROVAL_READ);
    }

    @Test
    void viewerCannotApproveWhenPermissionDenied() {
        doThrow(new TenantAccessService.TenantAccessDeniedException("denied"))
                .when(access).requireWorkspacePermission(Permission.APPROVAL_MANAGE);

        assertThrows(TenantAccessService.TenantAccessDeniedException.class,
                () -> controller.approve(UUID.randomUUID().toString()));
        verify(service, never()).approveApprovalItem(anyString());
    }

    @Test
    void viewerCannotRejectWhenPermissionDenied() {
        doThrow(new TenantAccessService.TenantAccessDeniedException("denied"))
                .when(access).requireWorkspacePermission(Permission.APPROVAL_MANAGE);

        assertThrows(TenantAccessService.TenantAccessDeniedException.class,
                () -> controller.reject(UUID.randomUUID().toString(), null));
        verify(service, never()).rejectApprovalItem(anyString(), any());
    }

    @Test
    void viewerCannotExecuteWhenPermissionDenied() {
        doThrow(new TenantAccessService.TenantAccessDeniedException("denied"))
                .when(access).requireWorkspacePermission(Permission.APPROVAL_EXECUTE);

        assertThrows(TenantAccessService.TenantAccessDeniedException.class,
                () -> controller.execute(UUID.randomUUID().toString()));
        verify(service, never()).executeApprovalItem(anyString());
    }

    @Test
    void idTamperingReturns404() {
        String id = UUID.randomUUID().toString();
        when(service.getApprovalById(id)).thenThrow(new ApprovalQueueService.ApprovalNotFoundException("not found"));

        var result = controller.get(id);

        assertEquals(404, result.getStatusCode().value());
    }

    private ApprovalItemResponse response(ApprovalStatus status) {
        String id = UUID.randomUUID().toString();
        return new ApprovalItemResponse(
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                ApprovalSourceModule.CAMPAIGN,
                "Campaign",
                UUID.randomUUID().toString(),
                ApprovalActionType.PAUSE_CAMPAIGN,
                "Pause campaign",
                "Review pause",
                null,
                null,
                ApprovalSeverity.HIGH,
                status,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                false
        );
    }
}
