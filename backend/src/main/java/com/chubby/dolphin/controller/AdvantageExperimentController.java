package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AdvantageExperiment;
import com.chubby.dolphin.repository.AdvantageExperimentRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.AdvantageExperimentService;
import lombok.extern.slf4j.Slf4j;
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
    private final SecurityUtils sec;

    public AdvantageExperimentController(AdvantageExperimentService experimentService,
                                         AdvantageExperimentRepository experimentRepo,
                                         SecurityUtils sec) {
        this.experimentService = experimentService;
        this.experimentRepo = experimentRepo;
        this.sec = sec;
    }

    /**
     * List all proposed, active, and completed experiments for the active workspace.
     */
    @GetMapping
    public ResponseEntity<List<AdvantageExperiment>> listExperiments() {
        return ResponseEntity.ok(experimentRepo.findByWorkspaceId(sec.currentWorkspaceId()));
    }

    /**
     * Propose/suggest a new Advantage+ targeting switch experiment for an eligible underperforming campaign.
     */
    @PostMapping("/propose")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<AdvantageExperiment> proposeExperiment(@RequestBody Map<String, String> body) {
        String campaignId = body.get("campaign_id");
        if (campaignId == null || campaignId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        AdvantageExperiment exp = experimentService.proposeAdvantagePlusExperiment(sec.currentWorkspaceId(), campaignId);
        return ResponseEntity.ok(exp);
    }

    /**
     * Activate Advantage+ targeting optimizations.
     */
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<AdvantageExperiment> activateExperiment(@PathVariable String id) {
        String workspaceId = sec.currentWorkspaceId();
        java.util.Optional<AdvantageExperiment> expOpt = experimentRepo.findById(id);
        if (expOpt.isEmpty()) return ResponseEntity.notFound().build();
        if (!expOpt.get().getWorkspaceId().equals(workspaceId)) {
            return ResponseEntity.status(403).build();
        }

        AdvantageExperiment exp = experimentService.activateAdvantagePlus(id);
        return ResponseEntity.ok(exp);
    }

    /**
     * Trigger evaluations of all active experiments (computes ROI deltas and tests safety rails).
     */
    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> evaluate() {
        experimentService.evaluateActiveExperiments();
        return ResponseEntity.ok(Map.of("success", true, "message", "Active Advantage+ experiments successfully evaluated."));
    }
}
