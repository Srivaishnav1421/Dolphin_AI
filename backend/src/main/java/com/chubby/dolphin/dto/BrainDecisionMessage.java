package com.chubby.dolphin.dto;

import java.io.Serializable;

public class BrainDecisionMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String decisionId;
    private String campaignId;
    private String action;
    private Double budgetAfter;

    public BrainDecisionMessage() {}

    public BrainDecisionMessage(String decisionId, String campaignId, String action, Double budgetAfter) {
        this.decisionId = decisionId;
        this.campaignId = campaignId;
        this.action = action;
        this.budgetAfter = budgetAfter;
    }

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Double getBudgetAfter() { return budgetAfter; }
    public void setBudgetAfter(Double budgetAfter) { this.budgetAfter = budgetAfter; }
}
