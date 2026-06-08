package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "competitor_ads")
public class CompetitorAd {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id")
    private String workspaceId;

    private String keyword;

    @Column(name = "page_name")
    private String pageName;

    @Column(name = "page_id", length = 50)
    private String pageId;

    @Column(name = "ad_text", columnDefinition = "TEXT")
    private String adText;

    @Column(name = "snapshot_url", length = 500)
    private String snapshotUrl;

    @Column(length = 30)
    private String format; // VIDEO, IMAGE, CAROUSEL

    @Column(name = "hook_type", length = 50)
    private String hookType; // QUESTION, STATEMENT, OFFER, URGENCY, PAIN_POINT

    @Column(name = "offer_type", length = 50)
    private String offerType; // DISCOUNT, FREE_TRIAL, LEAD_GEN, DEMO, INFORMATION

    @Column(length = 30)
    private String emotion; // FEAR, JOY, CURIOSITY, TRUST, URGENCY

    @Column(name = "quality_score")
    private Integer qualityScore; // 1-10

    @Column(name = "delivery_start_date")
    private LocalDate deliveryStartDate;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CompetitorAd() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getAdText() { return adText; }
    public void setAdText(String adText) { this.adText = adText; }
    public String getSnapshotUrl() { return snapshotUrl; }
    public void setSnapshotUrl(String snapshotUrl) { this.snapshotUrl = snapshotUrl; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getHookType() { return hookType; }
    public void setHookType(String hookType) { this.hookType = hookType; }
    public String getOfferType() { return offerType; }
    public void setOfferType(String offerType) { this.offerType = offerType; }
    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public LocalDate getDeliveryStartDate() { return deliveryStartDate; }
    public void setDeliveryStartDate(LocalDate deliveryStartDate) { this.deliveryStartDate = deliveryStartDate; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
