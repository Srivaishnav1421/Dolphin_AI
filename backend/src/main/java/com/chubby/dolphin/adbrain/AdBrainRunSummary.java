package com.chubby.dolphin.adbrain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ad_brain_runs", indexes = {
        @Index(name = "idx_ad_brain_runs_workspace_started", columnList = "workspace_id, started_at"),
        @Index(name = "idx_ad_brain_runs_account_started", columnList = "account_id, started_at"),
        @Index(name = "idx_ad_brain_runs_status", columnList = "status"),
        @Index(name = "idx_ad_brain_runs_started", columnList = "started_at")
})
@Data
@NoArgsConstructor
public class AdBrainRunSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;
    private UUID workspaceId;
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdBrainRunStatus status = AdBrainRunStatus.RUNNING;

    @Column(nullable = false)
    private int campaignsEvaluated;

    @Column(nullable = false)
    private int evaluationsCreated;

    @Column(nullable = false)
    private int approvalItemsCreated;

    @Column(nullable = false)
    private int duplicateApprovalsSkipped;

    @Column(nullable = false)
    private int risksCreated;

    @Column(nullable = false)
    private int opportunitiesCreated;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private UUID createdBy;
}
