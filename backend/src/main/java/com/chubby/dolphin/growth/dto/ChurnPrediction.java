package com.chubby.dolphin.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnPrediction {
    private double churnProbability;
    private List<String> riskFactors;
    private List<String> recommendedInterventions;
}
