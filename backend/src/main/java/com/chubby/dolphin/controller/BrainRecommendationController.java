package com.chubby.dolphin.controller;

import com.chubby.dolphin.brain.BrainRecommendationService;
import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/brain/recommendations")
@Slf4j
public class BrainRecommendationController {

    private final BrainRecommendationService recommendationService;
    private final SecurityUtils sec;

    public BrainRecommendationController(BrainRecommendationService recommendationService, SecurityUtils sec) {
        this.recommendationService = recommendationService;
        this.sec = sec;
    }

    @GetMapping
    public ResponseEntity<List<BrainDecision>> getRecommendations() {
        String workspaceId = sec.currentAccountId();
        List<BrainDecision> list = recommendationService.getRecentRecommendations(workspaceId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BrainDecision> getRecommendation(@PathVariable String id) {
        Optional<BrainDecision> opt = recommendationService.getRecommendationById(id);
        return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/evaluate")
    public ResponseEntity<List<BrainDecision>> evaluate() {
        String workspaceId = sec.currentAccountId();
        List<BrainDecision> list = recommendationService.evaluateAndSave(workspaceId);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<?> approve(@PathVariable String id) {
        try {
            BrainDecision approved = recommendationService.approve(id, sec.currentEmail());
            return ResponseEntity.ok(approved);
        } catch (Exception e) {
            log.error("Failed to approve recommendation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<?> reject(@PathVariable String id) {
        try {
            BrainDecision rejected = recommendationService.reject(id, sec.currentEmail());
            return ResponseEntity.ok(rejected);
        } catch (Exception e) {
            log.error("Failed to reject recommendation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
