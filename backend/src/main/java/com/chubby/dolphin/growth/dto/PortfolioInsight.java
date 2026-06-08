package com.chubby.dolphin.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioInsight {
    private String workspaceId;
    private String workspaceName;
    private double healthScore;
    private String healthClassification;
    private double churnRisk;
    private ClvForecast clvForecast;
    private double priorityScore;
    private boolean isNeglected;
    private double resourceAllocationScore;
}
