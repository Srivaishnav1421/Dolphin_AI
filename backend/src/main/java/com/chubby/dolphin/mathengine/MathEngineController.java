package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/math-engine")
@RequiredArgsConstructor
public class MathEngineController {

    private final CampaignMathEvaluationService evaluationService;
    private final AccessControlService access;

    @PostMapping("/campaigns/{id}/evaluate")
    public ResponseEntity<?> evaluateCampaign(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.MATH_ENGINE_RUN);
        try {
            return ResponseEntity.ok(evaluationService.evaluateCampaign(UUID.fromString(id)));
        } catch (CampaignMathEvaluationService.MathEvaluationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/workspace/evaluate")
    public ResponseEntity<?> evaluateWorkspace() {
        access.requireWorkspacePermission(Permission.MATH_ENGINE_RUN);
        return ResponseEntity.ok(evaluationService.evaluateActiveCampaignsForCurrentWorkspace());
    }

    @GetMapping("/campaigns/{id}/evaluations")
    public ResponseEntity<?> campaignEvaluations(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_METRICS_READ);
        try {
            return ResponseEntity.ok(evaluationService.getLatestEvaluationsForCampaign(UUID.fromString(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/signals/latest")
    public ResponseEntity<?> latestSignals() {
        access.requireWorkspacePermission(Permission.CAMPAIGN_METRICS_READ);
        return ResponseEntity.ok(evaluationService.getLatestWorkspaceSignals(UUID.fromString(access.currentWorkspaceId())));
    }
}
