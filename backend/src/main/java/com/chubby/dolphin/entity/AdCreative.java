package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AI-generated or manually created ad creatives.
 * Tracks headline, body, CTA, image, and performance metrics.
 */
@Entity
@Table(name = "ad_creatives", indexes = {
    @Index(name = "idx_creative_campaign", columnList = "campaign_id"),
    @Index(name = "idx_creative_workspace",  columnList = "account_id"),
    @Index(name = "idx_creative_status",   columnList = "status")
})
public class AdCreative {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String workspaceId;

    private String campaignId;

    /** The Meta Ad ID if published to Meta */
    private String metaAdId;

    // ── Creative Content ─────────────────────────────────────────────
    @Column(length = 500)
    private String headline;

    @Column(length = 2000)
    private String body;

    private String callToAction;     // LEARN_MORE, SHOP_NOW, SIGN_UP, BOOK_NOW, CONTACT_US

    private String imageUrl;         // URL to creative image
    private String videoUrl;         // URL to creative video (if applicable)

    /** FACEBOOK_FEED, INSTAGRAM_FEED, INSTAGRAM_STORY, REELS, AUDIENCE_NETWORK */
    private String platform;

    /** DRAFT, REVIEW, APPROVED, ACTIVE, PAUSED, ARCHIVED */
    private String status = "DRAFT";

    /** How this creative was generated: AI_GENERATED, MANUAL, AI_ASSISTED */
    private String generatedBy = "MANUAL";

    /** The AI prompt used to generate this creative (if AI-generated) */
    @Column(length = 2000)
    private String generationPrompt;

    // ── Performance Metrics ──────────────────────────────────────────
    private Double predictedCtr;
    private Double actualCtr;
    private Double actualCpc;
    private Long impressions = 0L;
    private Long clicks = 0L;
    private Long conversions = 0L;
    private Double spend = 0.0;

    // ── A/B Test Tracking ────────────────────────────────────────────
    private String abTestGroup;      // A, B, C, etc.
    private String abTestId;         // Links variants together

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public AdCreative() {}

    // ── Getters and Setters ──────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public String getMetaAdId() { return metaAdId; }
    public void setMetaAdId(String metaAdId) { this.metaAdId = metaAdId; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCallToAction() { return callToAction; }
    public void setCallToAction(String callToAction) { this.callToAction = callToAction; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }

    public String getGenerationPrompt() { return generationPrompt; }
    public void setGenerationPrompt(String generationPrompt) { this.generationPrompt = generationPrompt; }

    public Double getPredictedCtr() { return predictedCtr; }
    public void setPredictedCtr(Double predictedCtr) { this.predictedCtr = predictedCtr; }

    public Double getActualCtr() { return actualCtr; }
    public void setActualCtr(Double actualCtr) { this.actualCtr = actualCtr; }

    public Double getActualCpc() { return actualCpc; }
    public void setActualCpc(Double actualCpc) { this.actualCpc = actualCpc; }

    public Long getImpressions() { return impressions; }
    public void setImpressions(Long impressions) { this.impressions = impressions; }

    public Long getClicks() { return clicks; }
    public void setClicks(Long clicks) { this.clicks = clicks; }

    public Long getConversions() { return conversions; }
    public void setConversions(Long conversions) { this.conversions = conversions; }

    public Double getSpend() { return spend; }
    public void setSpend(Double spend) { this.spend = spend; }

    public String getAbTestGroup() { return abTestGroup; }
    public void setAbTestGroup(String abTestGroup) { this.abTestGroup = abTestGroup; }

    public String getAbTestId() { return abTestId; }
    public void setAbTestId(String abTestId) { this.abTestId = abTestId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
