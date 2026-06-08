package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Records every decision the Brain AI makes.
 * Tracks what was decided, confidence level, whether it was auto-executed
 * or required approval, and the outcome. This is the learning/feedback loop.
 */
@Entity
@Table(name = "brain_decisions", indexes = {
    @Index(name = "idx_brain_dec_workspace",  columnList = "account_id, created_at"),
    @Index(name = "idx_brain_dec_campaign", columnList = "campaign_id"),
    @Index(name = "idx_brain_dec_status",   columnList = "status")
})
public class BrainDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String workspaceId;

    /** The campaign this decision is about (nullable for account-wide decisions) */
    private String campaignId;
    private String campaignName;

    /**
     * Decision type:
     *   PAUSE, RESUME, SCALE_UP, SCALE_DOWN, BUDGET_REALLOCATE,
     *   CREATE_CAMPAIGN, CHANGE_AUDIENCE, CHANGE_CREATIVE
     */
    @Column(nullable = false)
    private String decisionType;

    /** The specific action recommended */
    @Column(length = 2000)
    private String action;

    /** AI confidence: 0.0 - 1.0 */
    private Double confidence;

    /** PENDING_APPROVAL, AUTO_EXECUTED, APPROVED, REJECTED, EXPIRED, FAILED */
    @Column(nullable = false)
    private String status = "PENDING_APPROVAL";

    /** Which LLM made this decision */
    private String llmProvider;

    /** Raw AI response for debugging */
    @Column(length = 4000)
    private String rawAiResponse;

    /** The reason/explanation for the decision */
    @Column(length = 2000)
    private String reason;

    /** If budget change: before and after values */
    private Double budgetBefore;
    private Double budgetAfter;

    /** Metrics at the time of decision (for learning) */
    private Double roasAtDecision;
    private Double ctrAtDecision;
    private Double cplAtDecision;
    private Double spentAtDecision;

    /** If executed, was the outcome positive? (filled by feedback loop) */
    private Boolean outcomePositive;
    private Double roasAfterExecution;

    /** Who approved/rejected (null for auto-executed) */
    private String approvedBy;
    private LocalDateTime approvedAt;

    /** When was this decision executed on Meta? */
    private LocalDateTime executedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    private Double riskScore = 0.0;
    private Double confidenceScore = 0.0;

    @Column(columnDefinition = "TEXT")
    private String campaignSnapshotJson;

    private String triggerMetrics;
    private String thresholdBreached;

    public BrainDecision() {}

    // ── Getters and Setters ──────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String campaignName) { this.campaignName = campaignName; }

    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLlmProvider() { return llmProvider; }
    public void setLlmProvider(String llmProvider) { this.llmProvider = llmProvider; }

    public String getRawAiResponse() { return rawAiResponse; }
    public void setRawAiResponse(String rawAiResponse) { this.rawAiResponse = rawAiResponse; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Double getBudgetBefore() { return budgetBefore; }
    public void setBudgetBefore(Double budgetBefore) { this.budgetBefore = budgetBefore; }

    public Double getBudgetAfter() { return budgetAfter; }
    public void setBudgetAfter(Double budgetAfter) { this.budgetAfter = budgetAfter; }

    public Double getRoasAtDecision() { return roasAtDecision; }
    public void setRoasAtDecision(Double roasAtDecision) { this.roasAtDecision = roasAtDecision; }

    public Double getCtrAtDecision() { return ctrAtDecision; }
    public void setCtrAtDecision(Double ctrAtDecision) { this.ctrAtDecision = ctrAtDecision; }

    public Double getCplAtDecision() { return cplAtDecision; }
    public void setCplAtDecision(Double cplAtDecision) { this.cplAtDecision = cplAtDecision; }

    public Double getSpentAtDecision() { return spentAtDecision; }
    public void setSpentAtDecision(Double spentAtDecision) { this.spentAtDecision = spentAtDecision; }

    public Boolean getOutcomePositive() { return outcomePositive; }
    public void setOutcomePositive(Boolean outcomePositive) { this.outcomePositive = outcomePositive; }

    public Double getRoasAfterExecution() { return roasAfterExecution; }
    public void setRoasAfterExecution(Double roasAfterExecution) { this.roasAfterExecution = roasAfterExecution; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getCampaignSnapshotJson() { return campaignSnapshotJson; }
    public void setCampaignSnapshotJson(String campaignSnapshotJson) { this.campaignSnapshotJson = campaignSnapshotJson; }

    public String getTriggerMetrics() { return triggerMetrics; }
    public void setTriggerMetrics(String triggerMetrics) { this.triggerMetrics = triggerMetrics; }

    public String getThresholdBreached() { return thresholdBreached; }
    public void setThresholdBreached(String thresholdBreached) { this.thresholdBreached = thresholdBreached; }
}
