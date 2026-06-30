package com.chubby.dolphin.adbrain;

import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ad-brain")
@RequiredArgsConstructor
public class AdBrainController {

    private final AdBrainService adBrainService;
    private final AccessControlService access;

    @PostMapping("/run")
    public ResponseEntity<?> run() {
        access.requireWorkspacePermission(Permission.AD_BRAIN_RUN);
        return ResponseEntity.ok(adBrainService.runCurrentWorkspace());
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        access.requireWorkspacePermission(Permission.AD_BRAIN_READ);
        return ResponseEntity.ok(adBrainService.latestStatus());
    }

    @GetMapping("/runs")
    public ResponseEntity<?> runs() {
        access.requireWorkspacePermission(Permission.AD_BRAIN_READ);
        return ResponseEntity.ok(adBrainService.recentRuns());
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<?> runById(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.AD_BRAIN_READ);
        try {
            return ResponseEntity.ok(adBrainService.runById(UUID.fromString(id)));
        } catch (AdBrainRunService.AdBrainRunNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/signals/latest")
    public ResponseEntity<?> latestSignals() {
        access.requireWorkspacePermission(Permission.AD_BRAIN_READ);
        return ResponseEntity.ok(adBrainService.latestSignals());
    }

    @GetMapping("/evaluations")
    public ResponseEntity<?> evaluations(@RequestParam(required = false) String runId) {
        access.requireWorkspacePermission(Permission.AD_BRAIN_READ);
        try {
            if (runId != null && !runId.isBlank()) {
                return ResponseEntity.ok(adBrainService.evaluationsForRun(UUID.fromString(runId)));
            }
            return ResponseEntity.ok(adBrainService.latestEvaluations());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/evaluations/{id}")
    public ResponseEntity<?> evaluationById(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.AD_BRAIN_READ);
        try {
            return ResponseEntity.ok(adBrainService.evaluationById(UUID.fromString(id)));
        } catch (com.chubby.dolphin.mathengine.CampaignMathEvaluationService.MathEvaluationNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
