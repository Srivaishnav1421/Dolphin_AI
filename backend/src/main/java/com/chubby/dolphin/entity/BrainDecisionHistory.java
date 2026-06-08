package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brain_decision_history", indexes = {
    @Index(name = "idx_bd_history_decision", columnList = "decisionId"),
    @Index(name = "idx_bd_history_campaign", columnList = "campaignId")
})
public class BrainDecisionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String decisionId;

    @Column(name = "account_id", nullable = false)
    private String workspaceId;

    @Column(nullable = false)
    private String campaignId;

    @Column(nullable = false)
    private String action;

    private Double confidenceScore;
    private Double riskScore;

    @Column(columnDefinition = "TEXT")
    private String metricsAtDecision;

    @Column(columnDefinition = "TEXT")
    private String thresholdsAtDecision;

    @Column(columnDefinition = "TEXT")
    private String campaignSnapshotJson;

    private String status;

    private LocalDateTime createdAt = LocalDateTime.now();

    public BrainDecisionHistory() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

    public String getMetricsAtDecision() { return metricsAtDecision; }
    public void setMetricsAtDecision(String metricsAtDecision) { this.metricsAtDecision = metricsAtDecision; }

    public String getThresholdsAtDecision() { return thresholdsAtDecision; }
    public void setThresholdsAtDecision(String thresholdsAtDecision) { this.thresholdsAtDecision = thresholdsAtDecision; }

    public String getCampaignSnapshotJson() { return campaignSnapshotJson; }
    public void setCampaignSnapshotJson(String campaignSnapshotJson) { this.campaignSnapshotJson = campaignSnapshotJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
