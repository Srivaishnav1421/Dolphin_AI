package com.chubby.dolphin.approval;

import com.chubby.dolphin.approval.dto.ApprovalExecutionResponse;
import com.chubby.dolphin.approval.dto.ApprovalItemCreateRequest;
import com.chubby.dolphin.approval.dto.ApprovalItemResponse;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.Organization;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalQueueService {

    private final ApprovalItemRepository repository;
    private final AccessControlService access;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApprovalItemResponse createApprovalItem(ApprovalItemCreateRequest request) {
        String workspaceId = access.currentWorkspaceId();
        User actor = access.currentUser();
        LocalDateTime now = LocalDateTime.now();

        ApprovalItem item = new ApprovalItem();
        item.setOrganizationId(uuid(actor.getOrganization() != null ? actor.getOrganization().getId() : request.organizationId()));
        item.setWorkspaceId(requiredUuid(workspaceId, "workspace context"));
        item.setAccountId(requiredUuid(workspaceId, "account context"));
        item.setSourceModule(request.sourceModule() != null ? request.sourceModule() : ApprovalSourceModule.SYSTEM);
        item.setSourceEntityType(clean(request.sourceEntityType(), 100));
        item.setSourceEntityId(uuid(request.sourceEntityId()));
        item.setActionType(request.actionType() != null ? request.actionType() : ApprovalActionType.OTHER);
        item.setTitle(requiredClean(request.title(), 255, "title"));
        item.setDescription(clean(request.description(), 4000));
        item.setRecommendationJson(clean(request.recommendationJson(), 8000));
        item.setMathSnapshotJson(clean(request.mathSnapshotJson(), 8000));
        item.setSeverity(request.severity() != null ? request.severity() : ApprovalSeverity.MEDIUM);
        item.setStatus(ApprovalStatus.PENDING);
        item.setRequiresExecution(request.requiresExecution() == null || request.requiresExecution());
        item.setCreatedBy(uuid(actor.getId()));
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setExpiresAt(request.expiresAt());

        ApprovalItem saved = repository.save(item);
        audit(saved, "APPROVAL_ITEM_CREATED", "old_status=null; new_status=PENDING; source=" + saved.getSourceModule() + "; action=" + saved.getActionType());
        return ApprovalItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalItemResponse> listApprovals() {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return repository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(ApprovalItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalItemResponse> listPendingApprovals() {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return repository.findByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, ApprovalStatus.PENDING).stream()
                .map(ApprovalItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalItemResponse getApprovalById(String id) {
        return ApprovalItemResponse.from(loadScoped(id));
    }

    @Transactional
    public ApprovalItemResponse approveApprovalItem(String id) {
        ApprovalItem item = loadScoped(id);
        if (item.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Only pending approvals can be approved.");
        }
        User actor = access.currentUser();
        item.setStatus(ApprovalStatus.APPROVED);
        item.setApprovedBy(uuid(actor.getId()));
        item.setApprovedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        ApprovalItem saved = repository.save(item);
        audit(saved, "APPROVAL_ITEM_APPROVED", "old_status=PENDING; new_status=APPROVED; source=" + saved.getSourceModule() + "; action=" + saved.getActionType());
        publishStatusChanged(saved);
        return ApprovalItemResponse.from(saved);
    }

    @Transactional
    public ApprovalItemResponse rejectApprovalItem(String id, String reason) {
        ApprovalItem item = loadScoped(id);
        if (item.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Only pending approvals can be rejected.");
        }
        User actor = access.currentUser();
        item.setStatus(ApprovalStatus.REJECTED);
        item.setRejectedBy(uuid(actor.getId()));
        item.setRejectedAt(LocalDateTime.now());
        item.setRejectionReason(clean(reason, 2000));
        item.setUpdatedAt(LocalDateTime.now());
        ApprovalItem saved = repository.save(item);
        audit(saved, "APPROVAL_ITEM_REJECTED", "old_status=PENDING; new_status=REJECTED; source=" + saved.getSourceModule()
                + "; action=" + saved.getActionType() + "; reason=" + auditLogService.redact(saved.getRejectionReason()));
        publishStatusChanged(saved);
        return ApprovalItemResponse.from(saved);
    }

    @Transactional
    public ApprovalExecutionResponse executeApprovalItem(String id) {
        ApprovalItem item = loadScoped(id);
        audit(item, "APPROVAL_EXECUTION_ATTEMPTED", "old_status=" + item.getStatus() + "; new_status=" + item.getStatus()
                + "; source=" + item.getSourceModule() + "; action=" + item.getActionType());
        if (item.getStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Only approved approval items can be executed.");
        }

        item.setExecutionStatus("NOT_CONNECTED");
        item.setExecutionResultJson("{\"message\":\"Approved, execution integration not connected yet.\"}");
        item.setUpdatedAt(LocalDateTime.now());
        ApprovalItem saved = repository.save(item);
        audit(saved, "APPROVAL_EXECUTION_FAILED", "old_status=APPROVED; new_status=APPROVED; execution_status=NOT_CONNECTED; source="
                + saved.getSourceModule() + "; action=" + saved.getActionType());
        return new ApprovalExecutionResponse(
                ApprovalItemResponse.from(saved),
                "Approved, execution integration not connected yet."
        );
    }

    @Transactional
    public ApprovalItemResponse markExecutionFailed(String id, String resultJson) {
        ApprovalItem item = loadScoped(id);
        item.setStatus(ApprovalStatus.FAILED);
        item.setExecutionStatus("FAILED");
        item.setExecutionResultJson(clean(resultJson, 8000));
        item.setUpdatedAt(LocalDateTime.now());
        ApprovalItem saved = repository.save(item);
        audit(saved, "APPROVAL_EXECUTION_FAILED", "status=FAILED");
        return ApprovalItemResponse.from(saved);
    }

    @Transactional
    public int expireOldApprovals() {
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        List<ApprovalItem> expired = repository.findByWorkspaceIdAndStatusAndExpiresAtBefore(
                workspaceId, ApprovalStatus.PENDING, LocalDateTime.now());
        for (ApprovalItem item : expired) {
            item.setStatus(ApprovalStatus.EXPIRED);
            item.setUpdatedAt(LocalDateTime.now());
            repository.save(item);
            audit(item, "APPROVAL_ITEM_EXPIRED", "expires_at=" + item.getExpiresAt());
        }
        return expired.size();
    }

    private ApprovalItem loadScoped(String id) {
        UUID approvalId = requiredUuid(id, "approval id");
        UUID workspaceId = requiredUuid(access.currentWorkspaceId(), "workspace context");
        return repository.findByIdAndWorkspaceId(approvalId, workspaceId)
                .orElseThrow(() -> new ApprovalNotFoundException("Approval item not found."));
    }

    private void audit(ApprovalItem item, String action, String details) {
        User actor = access.currentUser();
        Organization organization = actor != null ? actor.getOrganization() : null;
        auditLogService.record(actor, organization, id(item.getWorkspaceId()), action,
                "ApprovalItem", id(item.getId()), auditLogService.redact(details));
    }

    private void publishStatusChanged(ApprovalItem item) {
        eventPublisher.publishEvent(new ApprovalStatusChangedEvent(
                id(item.getId()),
                item.getSourceModule(),
                item.getSourceEntityType(),
                id(item.getSourceEntityId()),
                item.getStatus()
        ));
    }

    private UUID requiredUuid(String value, String field) {
        UUID parsed = uuid(value);
        if (parsed == null) {
            throw new IllegalArgumentException(field + " must be a valid UUID.");
        }
        return parsed;
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String id(UUID value) {
        return value == null ? null : value.toString();
    }

    private String requiredClean(String value, int maxLength, String field) {
        String cleaned = clean(value, maxLength);
        if (cleaned == null || cleaned.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return cleaned;
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    public static class ApprovalNotFoundException extends RuntimeException {
        public ApprovalNotFoundException(String message) {
            super(message);
        }
    }
}
