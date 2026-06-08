package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily snapshot of campaign metrics from Meta Ads API.
 * Stores historical performance data for trend analysis,
 * EMAS calculations, and AI learning.
 */
@Entity
@Table(name = "metric_snapshots", indexes = {
    @Index(name = "idx_metrics_campaign_date", columnList = "campaign_id, snapshot_date"),
    @Index(name = "idx_metrics_account_date",  columnList = "account_id, snapshot_date")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_metrics_campaign_date", columnNames = {"campaign_id", "snapshot_date"})
})
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String campaignId;

    private String campaignName;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    // ── Raw Metrics from Meta ──────────────────────────────────────
    private Long impressions = 0L;
    private Long reach = 0L;
    private Long clicks = 0L;
    private Long linkClicks = 0L;
    private Double spend = 0.0;
    private Long conversions = 0L;
    private Double revenue = 0.0;
    private Long leads = 0L;

    // ── Computed Metrics ────────────────────────────────────────────
    private Double ctr = 0.0;         // Click-Through Rate
    private Double cpc = 0.0;         // Cost Per Click
    private Double cpl = 0.0;         // Cost Per Lead
    private Double cpa = 0.0;         // Cost Per Acquisition
    private Double roas = 0.0;        // Return On Ad Spend
    private Double frequency = 0.0;   // Avg times each person saw the ad

    // ── EMAS (Exponential Moving Average Score) ────────────────────
    private Double emasScore = 0.0;
    private Double emasRoas = 0.0;
    private Double emasCtr = 0.0;

    private LocalDateTime createdAt = LocalDateTime.now();

    public MetricSnapshot() {}

    // ── Getters and Setters ──────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public String getCampaignName() { return campaignName; }
    public void setCampaignName(String campaignName) { this.campaignName = campaignName; }

    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }

    public Long getImpressions() { return impressions; }
    public void setImpressions(Long impressions) { this.impressions = impressions; }

    public Long getReach() { return reach; }
    public void setReach(Long reach) { this.reach = reach; }

    public Long getClicks() { return clicks; }
    public void setClicks(Long clicks) { this.clicks = clicks; }

    public Long getLinkClicks() { return linkClicks; }
    public void setLinkClicks(Long linkClicks) { this.linkClicks = linkClicks; }

    public Double getSpend() { return spend; }
    public void setSpend(Double spend) { this.spend = spend; }

    public Long getConversions() { return conversions; }
    public void setConversions(Long conversions) { this.conversions = conversions; }

    public Double getRevenue() { return revenue; }
    public void setRevenue(Double revenue) { this.revenue = revenue; }

    public Long getLeads() { return leads; }
    public void setLeads(Long leads) { this.leads = leads; }

    public Double getCtr() { return ctr; }
    public void setCtr(Double ctr) { this.ctr = ctr; }

    public Double getCpc() { return cpc; }
    public void setCpc(Double cpc) { this.cpc = cpc; }

    public Double getCpl() { return cpl; }
    public void setCpl(Double cpl) { this.cpl = cpl; }

    public Double getCpa() { return cpa; }
    public void setCpa(Double cpa) { this.cpa = cpa; }

    public Double getRoas() { return roas; }
    public void setRoas(Double roas) { this.roas = roas; }

    public Double getFrequency() { return frequency; }
    public void setFrequency(Double frequency) { this.frequency = frequency; }

    public Double getEmasScore() { return emasScore; }
    public void setEmasScore(Double emasScore) { this.emasScore = emasScore; }

    public Double getEmasRoas() { return emasRoas; }
    public void setEmasRoas(Double emasRoas) { this.emasRoas = emasRoas; }

    public Double getEmasCtr() { return emasCtr; }
    public void setEmasCtr(Double emasCtr) { this.emasCtr = emasCtr; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
