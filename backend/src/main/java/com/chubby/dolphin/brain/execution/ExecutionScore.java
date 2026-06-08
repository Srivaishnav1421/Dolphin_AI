package com.chubby.dolphin.brain.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionScore {
    private Double successRate;
    private Double impactScore;
    private Double revenueImpact;
    private Double confidenceAdjustment;
}
