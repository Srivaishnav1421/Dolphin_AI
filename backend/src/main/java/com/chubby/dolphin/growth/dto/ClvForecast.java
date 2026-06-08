package com.chubby.dolphin.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClvForecast {
    private double currentClv;
    private double predictedClv;
    private double growthPotential;
}
