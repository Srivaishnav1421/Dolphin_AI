package com.chubby.dolphin.brain.strategy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetRecommendation {
    private List<String> underfundedCampaigns;
    private List<String> overfundedCampaigns;
    private Double wasteDetected;
    private List<String> scalingOpportunities;
}
