package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import com.chubby.dolphin.security.EncryptionConverter;
import java.time.LocalDateTime;

/**
 * Stores a user's connection to their Meta (Facebook/Instagram) Ad Account.
 * Each account can connect multiple Meta ad accounts.
 * The access token is a long-lived token obtained via OAuth2 flow.
 */
@Entity
@Table(name = "meta_connections", indexes = {
    @Index(name = "idx_meta_conn_workspace", columnList = "account_id"),
    @Index(name = "idx_meta_conn_status",  columnList = "token_status")
})
public class MetaConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String workspaceId;

    /** Facebook User ID from OAuth */
    private String metaUserId;

    /** Meta Ad Account ID — format: act_XXXXXXXXX */
    @Column(nullable = false)
    private String metaAdAccountId;

    /** Connected Facebook Page ID */
    private String metaPageId;
    private String metaPageName;

    /** Business Manager ID (if applicable) */
    private String metaBusinessId;

    /** Long-lived access token (encrypted at rest) */
    @Column(length = 1000, nullable = false)
    @Convert(converter = EncryptionConverter.class)
    private String accessToken;

    /** VALID, EXPIRED, REVOKED, ERROR */
    private String tokenStatus = "VALID";

    private LocalDateTime tokenExpiresAt;
    private LocalDateTime lastSyncAt;

    /** If true, the Brain AI can execute actions (pause, budget changes) on Meta */
    private boolean autoManageEnabled = false;

    /** Maximum daily spend cap — safety rail */
    private Double maxDailySpend = 10000.0;

    /** Auto-pause campaigns below this ROAS */
    private Double pauseRoasThreshold = 1.5;

    /** Scale-up campaigns above this ROAS */
    private Double scaleUpRoasThreshold = 3.0;

    /** Maximum budget change per AI decision (percentage) */
    private Double maxBudgetChangePercent = 30.0;

    /** Connected ad account name (from Meta) */
    private String adAccountName;

    /** Currency of the ad account */
    private String currency = "INR";

    /** Account timezone */
    private String timezone = "Asia/Kolkata";

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MetaConnection() {}

    // ── Getters and Setters ──────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getMetaUserId() { return metaUserId; }
    public void setMetaUserId(String metaUserId) { this.metaUserId = metaUserId; }

    public String getMetaAdAccountId() { return metaAdAccountId; }
    public void setMetaAdAccountId(String metaAdAccountId) { this.metaAdAccountId = metaAdAccountId; }

    public String getMetaPageId() { return metaPageId; }
    public void setMetaPageId(String metaPageId) { this.metaPageId = metaPageId; }

    public String getMetaPageName() { return metaPageName; }
    public void setMetaPageName(String metaPageName) { this.metaPageName = metaPageName; }

    public String getMetaBusinessId() { return metaBusinessId; }
    public void setMetaBusinessId(String metaBusinessId) { this.metaBusinessId = metaBusinessId; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getTokenStatus() { return tokenStatus; }
    public void setTokenStatus(String tokenStatus) { this.tokenStatus = tokenStatus; }

    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public boolean isAutoManageEnabled() { return autoManageEnabled; }
    public void setAutoManageEnabled(boolean autoManageEnabled) { this.autoManageEnabled = autoManageEnabled; }

    public Double getMaxDailySpend() { return maxDailySpend; }
    public void setMaxDailySpend(Double maxDailySpend) { this.maxDailySpend = maxDailySpend; }

    public Double getPauseRoasThreshold() { return pauseRoasThreshold; }
    public void setPauseRoasThreshold(Double pauseRoasThreshold) { this.pauseRoasThreshold = pauseRoasThreshold; }

    public Double getScaleUpRoasThreshold() { return scaleUpRoasThreshold; }
    public void setScaleUpRoasThreshold(Double scaleUpRoasThreshold) { this.scaleUpRoasThreshold = scaleUpRoasThreshold; }

    public Double getMaxBudgetChangePercent() { return maxBudgetChangePercent; }
    public void setMaxBudgetChangePercent(Double maxBudgetChangePercent) { this.maxBudgetChangePercent = maxBudgetChangePercent; }

    public String getAdAccountName() { return adAccountName; }
    public void setAdAccountName(String adAccountName) { this.adAccountName = adAccountName; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
