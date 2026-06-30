package com.chubby.dolphin.approval;

import com.chubby.dolphin.approval.dto.ApprovalItemCreateRequest;
import com.chubby.dolphin.approval.dto.ApprovalRejectRequest;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalQueueController {

    private final ApprovalQueueService approvalQueueService;
    private final AccessControlService access;

    @GetMapping
    public ResponseEntity<?> list() {
        access.requireWorkspacePermission(Permission.APPROVAL_READ);
        return ResponseEntity.ok(approvalQueueService.listApprovals());
    }

    @GetMapping("/pending")
    public ResponseEntity<?> pending() {
        access.requireWorkspacePermission(Permission.APPROVAL_READ);
        return ResponseEntity.ok(approvalQueueService.listPendingApprovals());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.APPROVAL_READ);
        try {
            return ResponseEntity.ok(approvalQueueService.getApprovalById(id));
        } catch (ApprovalQueueService.ApprovalNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ApprovalItemCreateRequest request) {
        access.requireWorkspacePermission(Permission.APPROVAL_MANAGE);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(approvalQueueService.createApprovalItem(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.APPROVAL_MANAGE);
        try {
            return ResponseEntity.ok(approvalQueueService.approveApprovalItem(id));
        } catch (ApprovalQueueService.ApprovalNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable String id, @RequestBody(required = false) ApprovalRejectRequest request) {
        access.requireWorkspacePermission(Permission.APPROVAL_MANAGE);
        try {
            return ResponseEntity.ok(approvalQueueService.rejectApprovalItem(
                    id,
                    request != null ? request.reason() : null
            ));
        } catch (ApprovalQueueService.ApprovalNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<?> execute(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.APPROVAL_EXECUTE);
        try {
            return ResponseEntity.ok(approvalQueueService.executeApprovalItem(id));
        } catch (ApprovalQueueService.ApprovalNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
