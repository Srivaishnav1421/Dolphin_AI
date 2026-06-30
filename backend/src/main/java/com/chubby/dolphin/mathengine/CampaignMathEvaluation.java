package com.chubby.dolphin.mathengine;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaign_math_evaluations", indexes = {
        @Index(name = "idx_campaign_math_eval_workspace_campaign_created", columnList = "workspace_id, campaign_id, created_at"),
        @Index(name = "idx_campaign_math_eval_account_campaign_created", columnList = "account_id, campaign_id, created_at"),
        @Index(name = "idx_campaign_math_eval_run_created", columnList = "run_id, created_at"),
        @Index(name = "idx_campaign_math_eval_workspace_run_created", columnList = "workspace_id, run_id, created_at"),
        @Index(name = "idx_campaign_math_eval_workspace_type_created", columnList = "workspace_id, evaluation_type, created_at"),
        @Index(name = "idx_campaign_math_eval_status_severity", columnList = "status, severity"),
        @Index(name = "idx_campaign_math_eval_action_type", columnList = "action_type"),
        @Index(name = "idx_campaign_math_eval_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
public class CampaignMathEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;
    private UUID workspaceId;
    private UUID accountId;
    private UUID campaignId;
    private UUID runId;

    @Column(nullable = false, length = 80)
    private String evaluationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MathEvaluationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MathSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private MathActionType actionType;

    private Double score;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String inputSnapshotJson;

    @Column(nullable = false, length = 100)
    private String formulaVersion;

    @Column(nullable = false)
    private Boolean requiresApproval = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
