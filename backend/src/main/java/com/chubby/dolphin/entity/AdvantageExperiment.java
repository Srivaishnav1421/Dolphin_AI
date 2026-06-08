package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "advantage_experiments")
public class AdvantageExperiment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id")
    private String workspaceId;

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "meta_campaign_id")
    private String metaCampaignId;

    @Column(name = "switched_at", nullable = false)
    private LocalDateTime switchedAt = LocalDateTime.now();

    @Column(name = "roas_before")
    private Double roasBefore;

    @Column(name = "roas_after_14d")
    private Double roasAfter14d;

    @Column(name = "roas_after_30d")
    private Double roasAfter30d;

    @Column(name = "net_roas_delta")
    private Double netRoasDelta;

    @Column(nullable = false)
    private String status = "SUGGESTED"; // SUGGESTED, ACTIVE, REVERTED, SUCCESS

    @Column(name = "reverted_at")
    private LocalDateTime revertedAt;

    @Column(name = "revert_reason", columnDefinition = "TEXT")
    private String revertReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public AdvantageExperiment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    public String getMetaCampaignId() { return metaCampaignId; }
    public void setMetaCampaignId(String metaCampaignId) { this.metaCampaignId = metaCampaignId; }
    public LocalDateTime getSwitchedAt() { return switchedAt; }
    public void setSwitchedAt(LocalDateTime switchedAt) { this.switchedAt = switchedAt; }
    public Double getRoasBefore() { return roasBefore; }
    public void setRoasBefore(Double roasBefore) { this.roasBefore = roasBefore; }
    public Double getRoasAfter14d() { return roasAfter14d; }
    public void setRoasAfter14d(Double roasAfter14d) { this.roasAfter14d = roasAfter14d; }
    public Double getRoasAfter30d() { return roasAfter30d; }
    public void setRoasAfter30d(Double roasAfter30d) { this.roasAfter30d = roasAfter30d; }
    public Double getNetRoasDelta() { return netRoasDelta; }
    public void setNetRoasDelta(Double netRoasDelta) { this.netRoasDelta = netRoasDelta; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRevertedAt() { return revertedAt; }
    public void setRevertedAt(LocalDateTime revertedAt) { this.revertedAt = revertedAt; }
    public String getRevertReason() { return revertReason; }
    public void setRevertReason(String revertReason) { this.revertReason = revertReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
