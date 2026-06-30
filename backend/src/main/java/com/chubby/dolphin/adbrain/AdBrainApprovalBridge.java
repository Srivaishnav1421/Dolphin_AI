package com.chubby.dolphin.adbrain;

import com.chubby.dolphin.approval.*;
import com.chubby.dolphin.mathengine.MathActionType;
import com.chubby.dolphin.mathengine.MathEvaluationStatus;
import com.chubby.dolphin.mathengine.dto.CampaignMathEvaluationResponse;
import com.chubby.dolphin.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdBrainApprovalBridge {

    private final ApprovalItemRepository approvalRepository;
    private final ApprovalRequiredActionService approvalRequiredActionService;
    private final AdBrainRecommendationMapper mapper;
    private final ObjectMapper objectMapper;

    public BridgeResult createApprovalIfNeeded(CampaignMathEvaluationResponse signal, UUID workspaceId, UUID organizationId, User actor) {
        if (!requiresApprovalItem(signal)) {
            return BridgeResult.skippedResult();
        }

        UUID campaignId = uuid(signal.campaignId());
        if (campaignId == null) {
            return BridgeResult.skippedResult();
        }

        ApprovalActionType approvalAction = mapper.toApprovalAction(signal.actionType());
        boolean duplicate = approvalRepository.existsByWorkspaceIdAndSourceModuleAndSourceEntityIdAndActionTypeAndStatus(
                workspaceId,
                ApprovalSourceModule.AD_BRAIN,
                campaignId,
                approvalAction,
                ApprovalStatus.PENDING
        );
        if (duplicate) {
            return BridgeResult.duplicateResult();
        }

        ApprovalItem item = approvalRequiredActionService.createRequiredAction(
                new ApprovalRequiredActionService.ApprovalRequiredActionRequest(
                        organizationId,
                        workspaceId,
                        workspaceId,
                        ApprovalSourceModule.AD_BRAIN,
                        "CAMPAIGN",
                        campaignId,
                        approvalAction,
                        title(signal, approvalAction),
                        signal.description(),
                        recommendationJson(signal, approvalAction),
                        mathSnapshotJson(signal),
                        mapper.toApprovalSeverity(signal.severity()),
                        actor
                )
        );
        return BridgeResult.createdResult(item);
    }

    private boolean requiresApprovalItem(CampaignMathEvaluationResponse signal) {
        return signal != null
                && Boolean.TRUE.equals(signal.requiresApproval())
                && signal.status() != MathEvaluationStatus.NOT_ENOUGH_DATA
                && signal.actionType() != null
                && signal.actionType() != MathActionType.NONE;
    }

    private String title(CampaignMathEvaluationResponse signal, ApprovalActionType approvalAction) {
        if (signal.actionType() == MathActionType.PAUSE_ALL_REQUIRED) {
            return "Pause all campaigns required";
        }
        String base = signal.title() != null && !signal.title().isBlank() ? signal.title() : "Ad Brain recommendation";
        return base + " - " + approvalAction.name().replace('_', ' ');
    }

    private String recommendationJson(CampaignMathEvaluationResponse signal, ApprovalActionType approvalAction) {
        return toJson(Map.of(
                "source", "AD_BRAIN",
                "mathEvaluationId", signal.id(),
                "runId", signal.runId() == null ? "" : signal.runId(),
                "campaignId", signal.campaignId() == null ? "" : signal.campaignId(),
                "evaluationType", signal.evaluationType(),
                "mathAction", signal.actionType().name(),
                "approvalAction", approvalAction.name(),
                "formulaVersion", signal.formulaVersion(),
                "score", signal.score() == null ? "" : signal.score(),
                "reason", signal.description() == null ? "" : signal.description()
        ));
    }

    private String mathSnapshotJson(CampaignMathEvaluationResponse signal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mathEvaluationId", signal.id());
        payload.put("runId", signal.runId() == null ? "" : signal.runId());
        payload.put("campaignId", signal.campaignId() == null ? "" : signal.campaignId());
        payload.put("evaluationType", signal.evaluationType());
        payload.put("status", signal.status().name());
        payload.put("severity", signal.severity().name());
        payload.put("actionType", signal.actionType().name());
        payload.put("title", signal.title() == null ? "" : signal.title());
        payload.put("reason", signal.description() == null ? "" : signal.description());
        payload.put("score", signal.score() == null ? "" : signal.score());
        payload.put("formulaVersion", signal.formulaVersion());
        payload.put("inputSnapshotJson", signal.inputSnapshotJson() == null ? "" : signal.inputSnapshotJson());
        return toJson(payload);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record BridgeResult(boolean created, boolean duplicate, ApprovalItem item) {
        static BridgeResult createdResult(ApprovalItem item) {
            return new BridgeResult(true, false, item);
        }

        static BridgeResult duplicateResult() {
            return new BridgeResult(false, true, null);
        }

        static BridgeResult skippedResult() {
            return new BridgeResult(false, false, null);
        }
    }
}
