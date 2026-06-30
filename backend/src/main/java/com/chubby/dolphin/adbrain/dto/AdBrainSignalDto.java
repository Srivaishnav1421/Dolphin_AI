package com.chubby.dolphin.adbrain.dto;

import com.chubby.dolphin.mathengine.MathActionType;
import com.chubby.dolphin.mathengine.MathEvaluationStatus;
import com.chubby.dolphin.mathengine.MathSeverity;

import java.time.LocalDateTime;

public record AdBrainSignalDto(
        String id,
        String campaignId,
        String evaluationType,
        MathEvaluationStatus status,
        MathSeverity severity,
        MathActionType actionType,
        Double score,
        String title,
        String description,
        String formulaVersion,
        Boolean requiresApproval,
        LocalDateTime createdAt
) {}
