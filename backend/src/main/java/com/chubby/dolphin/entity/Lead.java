package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leads", indexes = {
    @Index(name = "idx_leads_workspace_id", columnList = "account_id"),
    @Index(name = "idx_leads_status",     columnList = "account_id, status"),
    @Index(name = "idx_leads_created",    columnList = "account_id, created_at")
})
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String workspaceId;

    private String name;
    private String source;       // INSTAGRAM, FACEBOOK, FORM, WHATSAPP, REFERRAL

    @Column(name = "campaign_id", length = 36)
    private String campaignId;

    @Column(name = "assigned_user_id", length = 36)
    private String assignedUserId;

    @Column(length = 1000)
    private String tags;

    @Column(length = 4000)
    private String notes;

    @Column(length = 2000)
    private String message;

    private String status;       // NEW, CONTACTED, QUALIFIED, WON, LOST

    @Column(length = 50)
    private String temperature; // HOT, WARM, COLD, UNKNOWN (from lead scoring)

    @Column(name = "pipeline_stage", length = 80)
    private String pipelineStage = "NEW_LEAD";

    private Double score;        // 0.0 – 1.0

    @Column(length = 30)
    private String priority = "MEDIUM";

    private Double budget;

    @Column(name = "interest_category", length = 255)
    private String interestCategory;

    @Column(length = 255)
    private String location;

    @Column(name = "last_contacted_at")
    private LocalDateTime lastContactedAt;

    @Column(name = "next_follow_up_at")
    private LocalDateTime nextFollowUpAt;

    @Column(name = "conversion_probability")
    private Double conversionProbability;

    @Column(name = "expected_revenue")
    private Double expectedRevenue;

    @Column(name = "lost_reason", length = 1000)
    private String lostReason;

    @Column(name = "ai_summary", length = 4000)
    private String aiSummary;

    @Column(name = "next_best_action", length = 1000)
    private String nextBestAction;

    private String budgetSignal;
    private String timelineSignal;
    private String intentSignal;
    private String locationSignal;

    @Column(length = 4000)
    private String geminiAnalysis;

    private String phone;
    private String email;
    private String ipAddress;
    
    @Column(length = 1000)
    private String userAgent;
    
    @Column(length = 500)
    private String sourceUrl;
    
    private Boolean optedOut = false;
    
    @Column(length = 2000)
    private String lastReply;
    private LocalDateTime lastReplyAt;
    
    private Boolean crmPushed = false;
    private String crmLeadId;
    private Boolean capiSent = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Lead() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAccountId() { return workspaceId; }
    public void setAccountId(String accountId) { this.workspaceId = accountId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public String getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(String assignedUserId) { this.assignedUserId = assignedUserId; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTemperature() { return temperature; }
    public void setTemperature(String temperature) { this.temperature = temperature; }

    public String getPipelineStage() { return pipelineStage; }
    public void setPipelineStage(String pipelineStage) { this.pipelineStage = pipelineStage; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Double getBudget() { return budget; }
    public void setBudget(Double budget) { this.budget = budget; }

    public String getInterestCategory() { return interestCategory; }
    public void setInterestCategory(String interestCategory) { this.interestCategory = interestCategory; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public LocalDateTime getLastContactedAt() { return lastContactedAt; }
    public void setLastContactedAt(LocalDateTime lastContactedAt) { this.lastContactedAt = lastContactedAt; }

    public LocalDateTime getNextFollowUpAt() { return nextFollowUpAt; }
    public void setNextFollowUpAt(LocalDateTime nextFollowUpAt) { this.nextFollowUpAt = nextFollowUpAt; }

    public Double getConversionProbability() { return conversionProbability; }
    public void setConversionProbability(Double conversionProbability) { this.conversionProbability = conversionProbability; }

    public Double getExpectedRevenue() { return expectedRevenue; }
    public void setExpectedRevenue(Double expectedRevenue) { this.expectedRevenue = expectedRevenue; }

    public String getLostReason() { return lostReason; }
    public void setLostReason(String lostReason) { this.lostReason = lostReason; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getNextBestAction() { return nextBestAction; }
    public void setNextBestAction(String nextBestAction) { this.nextBestAction = nextBestAction; }

    public String getBudgetSignal() { return budgetSignal; }
    public void setBudgetSignal(String budgetSignal) { this.budgetSignal = budgetSignal; }

    public String getTimelineSignal() { return timelineSignal; }
    public void setTimelineSignal(String timelineSignal) { this.timelineSignal = timelineSignal; }

    public String getIntentSignal() { return intentSignal; }
    public void setIntentSignal(String intentSignal) { this.intentSignal = intentSignal; }

    public String getLocationSignal() { return locationSignal; }
    public void setLocationSignal(String locationSignal) { this.locationSignal = locationSignal; }

    public String getGeminiAnalysis() { return geminiAnalysis; }
    public void setGeminiAnalysis(String geminiAnalysis) { this.geminiAnalysis = geminiAnalysis; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public Boolean getOptedOut() { return optedOut; }
    public void setOptedOut(Boolean optedOut) { this.optedOut = optedOut; }

    public String getLastReply() { return lastReply; }
    public void setLastReply(String lastReply) { this.lastReply = lastReply; }

    public LocalDateTime getLastReplyAt() { return lastReplyAt; }
    public void setLastReplyAt(LocalDateTime lastReplyAt) { this.lastReplyAt = lastReplyAt; }

    public Boolean getCrmPushed() { return crmPushed; }
    public void setCrmPushed(Boolean crmPushed) { this.crmPushed = crmPushed; }

    public String getCrmLeadId() { return crmLeadId; }
    public void setCrmLeadId(String crmLeadId) { this.crmLeadId = crmLeadId; }

    public Boolean getCapiSent() { return capiSent; }
    public void setCapiSent(Boolean capiSent) { this.capiSent = capiSent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
