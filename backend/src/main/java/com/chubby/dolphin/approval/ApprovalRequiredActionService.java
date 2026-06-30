package com.chubby.dolphin.approval;

import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalRequiredActionService {

    private final ApprovalItemRepository approvalRepository;
    private final AuditLogService auditLogService;

    public ApprovalItem createRequiredAction(ApprovalRequiredActionRequest request) {
        ApprovalItem item = new ApprovalItem();
        item.setOrganizationId(request.organizationId());
        item.setWorkspaceId(request.workspaceId());
        item.setAccountId(request.accountId() != null ? request.accountId() : request.workspaceId());
        item.setSourceModule(request.sourceModule() != null ? request.sourceModule() : ApprovalSourceModule.SYSTEM);
        item.setSourceEntityType(clean(request.sourceEntityType(), 100));
        item.setSourceEntityId(request.sourceEntityId());
        item.setActionType(request.actionType() != null ? request.actionType() : ApprovalActionType.OTHER);
        item.setTitle(requiredClean(request.title(), 255, "title"));
        item.setDescription(clean(request.description(), 4000));
        item.setRecommendationJson(clean(request.recommendationJson(), 8000));
        item.setMathSnapshotJson(clean(request.mathSnapshotJson(), 8000));
        item.setSeverity(request.severity() != null ? request.severity() : ApprovalSeverity.MEDIUM);
        item.setStatus(ApprovalStatus.PENDING);
        item.setRequiresExecution(true);
        item.setCreatedBy(userId(request.actor()));
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        ApprovalItem saved = approvalRepository.save(item);
        auditLogService.record(
                request.actor(),
                request.actor() != null ? request.actor().getOrganization() : null,
                id(saved.getWorkspaceId()),
                "APPROVAL_ITEM_CREATED",
                "ApprovalItem",
                id(saved.getId()),
                auditLogService.redact("old_status=null; new_status=PENDING; source=" + saved.getSourceModule()
                        + "; action=" + saved.getActionType())
        );
        return saved;
    }

    private UUID userId(User user) {
        if (user == null || user.getId() == null || user.getId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(user.getId());
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

    public record ApprovalRequiredActionRequest(
            UUID organizationId,
            UUID workspaceId,
            UUID accountId,
            ApprovalSourceModule sourceModule,
            String sourceEntityType,
            UUID sourceEntityId,
            ApprovalActionType actionType,
            String title,
            String description,
            String recommendationJson,
            String mathSnapshotJson,
            ApprovalSeverity severity,
            User actor
    ) {
    }
}
