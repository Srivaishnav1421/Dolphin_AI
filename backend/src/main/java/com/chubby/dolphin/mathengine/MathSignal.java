package com.chubby.dolphin.mathengine;

import java.util.LinkedHashMap;
import java.util.Map;

public record MathSignal(
        String evaluationType,
        MathEvaluationStatus status,
        MathSeverity severity,
        MathActionType actionType,
        Double score,
        String title,
        String description,
        Map<String, Object> inputSnapshot,
        String formulaVersion,
        boolean requiresApproval
) {

    public static MathSignal of(String evaluationType,
                                MathEvaluationStatus status,
                                MathSeverity severity,
                                MathActionType actionType,
                                Double score,
                                String title,
                                String description,
                                MathInputSnapshot snapshot,
                                String formulaVersion,
                                boolean requiresApproval) {
        return new MathSignal(
                evaluationType,
                status,
                severity,
                actionType,
                score,
                title,
                description,
                snapshot != null ? new LinkedHashMap<>(snapshot.values()) : Map.of(),
                formulaVersion,
                requiresApproval
        );
    }

    public boolean isRiskyAction() {
        return requiresApproval || actionType == MathActionType.PAUSE_ALL_REQUIRED
                || actionType == MathActionType.PAUSE_CAMPAIGN
                || actionType == MathActionType.KILL_CAMPAIGN
                || actionType == MathActionType.CHANGE_BUDGET
                || actionType == MathActionType.CHANGE_OBJECTIVE
                || actionType == MathActionType.INCREASE_BUDGET;
    }
}
