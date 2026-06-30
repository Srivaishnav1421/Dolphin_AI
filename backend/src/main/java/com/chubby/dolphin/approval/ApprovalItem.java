package com.chubby.dolphin.approval;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_items", indexes = {
        @Index(name = "idx_approval_items_workspace_status", columnList = "workspace_id, status"),
        @Index(name = "idx_approval_items_account_status", columnList = "account_id, status"),
        @Index(name = "idx_approval_items_organization_status", columnList = "organization_id, status"),
        @Index(name = "idx_approval_items_source_module_status", columnList = "source_module, status"),
        @Index(name = "idx_approval_items_created_at", columnList = "created_at"),
        @Index(name = "idx_approval_items_status_severity", columnList = "status, severity")
})
@Data
@NoArgsConstructor
public class ApprovalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;
    private UUID workspaceId;
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApprovalSourceModule sourceModule;

    @Column(length = 100)
    private String sourceEntityType;

    private UUID sourceEntityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private ApprovalActionType actionType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String recommendationJson;

    @Column(columnDefinition = "TEXT")
    private String mathSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(nullable = false)
    private Boolean requiresExecution = true;

    @Column(length = 40)
    private String executionStatus;

    @Column(columnDefinition = "TEXT")
    private String executionResultJson;

    private UUID createdBy;
    private UUID approvedBy;
    private UUID rejectedBy;
    private UUID executedBy;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime executedAt;
    private LocalDateTime expiresAt;
}
