package com.chubby.dolphin.brain.strategy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueForecast {
    private Double revenue7d;
    private Double spend7d;
    private Double profit7d;

    private Double revenue30d;
    private Double spend30d;
    private Double profit30d;

    private Double revenue90d;
    private Double spend90d;
    private Double profit90d;
}
