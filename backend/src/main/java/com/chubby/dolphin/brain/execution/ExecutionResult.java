package com.chubby.dolphin.brain.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {
    private String id;
    private String decisionId;
    private ExecutionStatus status;
    private String errorDetails;
    private LocalDateTime executedAt;
    private String campaignId;
    private String decisionType;
    private Double budgetBefore;
    private Double budgetAfter;
}
