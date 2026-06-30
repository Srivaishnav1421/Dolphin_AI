package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AdvantageExperiment;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.AdvantageExperimentRepository;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.AdvantageExperimentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advantage-experiments")
@Slf4j
public class AdvantageExperimentController {

    private final AdvantageExperimentService experimentService;
    private final AdvantageExperimentRepository experimentRepo;
    private final CampaignRepository campaignRepo;
    private final SecurityUtils sec;
    private final AccessControlService access;

    public AdvantageExperimentController(AdvantageExperimentService experimentService,
                                         AdvantageExperimentRepository experimentRepo,
                                         CampaignRepository campaignRepo,
                                         SecurityUtils sec,
                                         AccessControlService access) {
        this.experimentService = experimentService;
        this.experimentRepo = experimentRepo;
        this.campaignRepo = campaignRepo;
        this.sec = sec;
        this.access = access;
    }

    /**
     * List all proposed, active, and completed experiments for the active workspace.
     */
    @GetMapping
    public ResponseEntity<List<AdvantageExperiment>> listExperiments() {
        access.requireWorkspacePermission(Permission.CAMPAIGN_METRICS_READ);
        return ResponseEntity.ok(experimentRepo.findByWorkspaceId(sec.currentWorkspaceId()));
    }

    /**
     * Propose/suggest a new Advantage+ targeting switch experiment for an eligible underperforming campaign.
     */
    @PostMapping("/propose")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<AdvantageExperiment> proposeExperiment(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_APPROVE_AI_ACTION);
        String campaignId = body.get("campaign_id");
        if (campaignId == null || campaignId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String workspaceId = sec.currentWorkspaceId();
        if (campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AdvantageExperiment exp = experimentService.proposeAdvantagePlusExperiment(workspaceId, campaignId);
        return ResponseEntity.ok(exp);
    }

    /**
     * Activate Advantage+ targeting optimizations.
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<?> activateExperiment(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_APPROVE_AI_ACTION);
        String workspaceId = sec.currentWorkspaceId();
        java.util.Optional<AdvantageExperiment> expOpt = experimentRepo.findByIdAndWorkspaceId(id, workspaceId);
        if (expOpt.isEmpty()) return ResponseEntity.notFound().build();

        try {
            AdvantageExperiment exp = experimentService.activateAdvantagePlus(id, workspaceId);
            return ResponseEntity.ok(exp);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(Map.of(
                    "error", e.getMessage(),
                    "experiment_id", id,
                    "next_step", "Connect Meta, sync campaigns, and retry activation from an approved experiment."
            ));
        }
    }

    /**
     * Trigger evaluations of all active experiments (computes ROI deltas and tests safety rails).
     */
    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> evaluate() {
        access.requireWorkspacePermission(Permission.CAMPAIGN_APPROVE_AI_ACTION);
        experimentService.evaluateActiveExperiments(sec.currentWorkspaceId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Active Advantage+ experiments successfully evaluated."));
    }
}
