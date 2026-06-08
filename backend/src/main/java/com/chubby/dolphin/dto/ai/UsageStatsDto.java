package com.chubby.dolphin.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatsDto {
    private Long totalRequests;
    private Long totalTokens;
    private Double totalCostUsd;
    private Double totalCostInr;
}
