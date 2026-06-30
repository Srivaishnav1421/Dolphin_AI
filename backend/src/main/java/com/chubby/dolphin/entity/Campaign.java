package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns", indexes = {
    @Index(name = "idx_campaigns_workspace_id", columnList = "account_id"),
    @Index(name = "idx_campaigns_status",     columnList = "account_id, status")
})
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String workspaceId;

    @Column(nullable = false)
    private String name;

    private String status;       // ACTIVE, PAUSED, COMPLETED
    private String objective;    // LEADS, CONVERSIONS, AWARENESS, TRAFFIC

    private Double budget;
    private Double targetCpl;
    private Double spent;
    private Double ctr;
    private Double cpl;
    private Double roas;
    private Double performanceScore;
    private String description;

    private Integer conversions = 0;
    private Integer daysOfData = 0;
    private Double conversionRate = 0.0;
    private Double spendVelocity = 0.0;

    private String metaCampaignId; // For Meta Ads API integration

    // ── Scheduler fields ─────────────────────────────────────────
    private Boolean pauseOnWeekends = false;
    private LocalDateTime scheduledEndAt;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Campaign() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }

    public Double getBudget() { return budget; }
    public void setBudget(Double budget) { this.budget = budget; }

    public Double getTargetCpl() { return targetCpl; }
    public void setTargetCpl(Double targetCpl) { this.targetCpl = targetCpl; }

    public Double getSpent() { return spent; }
    public void setSpent(Double spent) { this.spent = spent; }

    public Double getCtr() { return ctr; }
    public void setCtr(Double ctr) { this.ctr = ctr; }

    public Double getCpl() { return cpl; }
    public void setCpl(Double cpl) { this.cpl = cpl; }

    public Double getRoas() { return roas; }
    public void setRoas(Double roas) { this.roas = roas; }

    public Double getPerformanceScore() { return performanceScore; }
    public void setPerformanceScore(Double performanceScore) { this.performanceScore = performanceScore; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMetaCampaignId() { return metaCampaignId; }
    public void setMetaCampaignId(String metaCampaignId) { this.metaCampaignId = metaCampaignId; }

    public Boolean getPauseOnWeekends() { return pauseOnWeekends; }
    public void setPauseOnWeekends(Boolean pauseOnWeekends) { this.pauseOnWeekends = pauseOnWeekends; }

    public LocalDateTime getScheduledEndAt() { return scheduledEndAt; }
    public void setScheduledEndAt(LocalDateTime scheduledEndAt) { this.scheduledEndAt = scheduledEndAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getConversions() { return conversions; }
    public void setConversions(Integer conversions) { this.conversions = conversions; }

    public Integer getDaysOfData() { return daysOfData; }
    public void setDaysOfData(Integer daysOfData) { this.daysOfData = daysOfData; }

    public Double getConversionRate() { return conversionRate; }
    public void setConversionRate(Double conversionRate) { this.conversionRate = conversionRate; }

    public Double getSpendVelocity() { return spendVelocity; }
    public void setSpendVelocity(Double spendVelocity) { this.spendVelocity = spendVelocity; }
}
