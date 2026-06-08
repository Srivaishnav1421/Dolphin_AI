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
public class StrategicPlan {
    private Double growthTarget;
    private Double expectedRevenue;
    private Double projectedSpend;
    private Double confidence;
    private List<String> risks;
    private List<String> milestones;
    private String plan7Day;
    private String plan30Day;
    private String plan90Day;
}
