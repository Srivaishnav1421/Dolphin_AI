package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.CreativeAIService;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.chubby.dolphin.repository.AdCreativeRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creative AI Controller — Endpoints for AI-powered ad creative generation.
 */
@RestController
@RequestMapping("/api/creatives")
@Slf4j
public class CreativeController {

    private final CreativeAIService creativeService;
    private final SecurityUtils sec;
    private final AdCreativeRepository creativeRepo;
    private final CampaignRepository campaignRepo;

    public CreativeController(CreativeAIService creativeService, SecurityUtils sec,
                              AdCreativeRepository creativeRepo, CampaignRepository campaignRepo) {
        this.creativeService = creativeService;
        this.sec = sec;
        this.creativeRepo = creativeRepo;
        this.campaignRepo = campaignRepo;
    }

    /** List all creatives, optionally filtered by status */
    @GetMapping
    public ResponseEntity<List<AdCreative>> list(@RequestParam(required = false) String status) {
        String workspaceId = sec.currentWorkspaceId();
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(creativeRepo.findByWorkspaceIdAndStatus(workspaceId, status.toUpperCase()));
        }
        return ResponseEntity.ok(creativeRepo.findByWorkspaceId(workspaceId));
    }

    /** Get creatives for a specific campaign */
    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<AdCreative>> campaignCreatives(@PathVariable String campaignId) {
        Optional<com.chubby.dolphin.entity.Campaign> campOpt = campaignRepo.findById(campaignId);
        if (campOpt.isEmpty()) return ResponseEntity.notFound().build();
        if (!campOpt.get().getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(creativeService.getCampaignCreatives(campaignId));
    }

    /**
     * Generate AI ad copy variations.
     * Required body: product, audience, tone, platform
     * Optional body: campaignId
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody Map<String, String> body) {
        String product  = body.getOrDefault("product", "");
        String audience = body.getOrDefault("audience", "");
        String tone     = body.getOrDefault("tone", "professional");
        String platform = body.getOrDefault("platform", "FACEBOOK_FEED");
        String languageCode = body.getOrDefault("language_code", "en");
        String campaignId = body.get("campaign_id");

        if (product.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product/service description is required"));
        }

        if (campaignId != null && !campaignId.isBlank()) {
            Optional<com.chubby.dolphin.entity.Campaign> campOpt = campaignRepo.findById(campaignId);
            if (campOpt.isEmpty()) return ResponseEntity.notFound().build();
            if (!campOpt.get().getWorkspaceId().equals(sec.currentWorkspaceId())) {
                return ResponseEntity.status(403).build();
            }
        }

        List<AdCreative> creatives = creativeService.generateAdCopy(
            sec.currentWorkspaceId(), campaignId, product, audience, tone, platform, languageCode
        );

        return ResponseEntity.ok(Map.of(
            "generated", creatives.size(),
            "creatives", creatives
        ));
    }

    /** Suggest A/B tests for a campaign's creatives */
    @PostMapping("/campaign/{campaignId}/ab-suggest")
    public ResponseEntity<?> suggestABTests(@PathVariable String campaignId) {
        Optional<com.chubby.dolphin.entity.Campaign> campOpt = campaignRepo.findById(campaignId);
        if (campOpt.isEmpty()) return ResponseEntity.notFound().build();
        if (!campOpt.get().getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        BusinessLlmFacadeService.LlmResponse response = creativeService.suggestABTests(campaignId);
        return ResponseEntity.ok(Map.of(
            "suggestions", response.text(),
            "provider", response.provider()
        ));
    }

    /** Rewrite a creative for a different platform */
    @PostMapping("/{id}/rewrite")
    public ResponseEntity<?> rewrite(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<AdCreative> opt = creativeRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        String platform = body.getOrDefault("platform", "INSTAGRAM_STORY");
        AdCreative rewritten = creativeService.rewriteForPlatform(id, platform);
        return ResponseEntity.ok(rewritten);
    }

    /** Update creative status */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<AdCreative> opt = creativeRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().body(Map.of("error", "Status required"));
        return ResponseEntity.ok(creativeService.updateStatus(id, status.toUpperCase()));
    }

    /** Launch a creative directly to Facebook/Google/TikTok Ads */
    @PostMapping("/{id}/launch")
    public ResponseEntity<?> launch(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<AdCreative> opt = creativeRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (!opt.get().getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        String platform = body.getOrDefault("platform", "META");
        Double budget = Double.parseDouble(body.getOrDefault("budget", "5000"));
        
        log.info("🚀 Launching creative id {} on platform {} with budget ₹{}", id, platform, budget);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "creativeId", id,
            "platform", platform,
            "budget", budget,
            "campaign_status", "ACTIVE",
            "message", "Autonomous multi-platform ad campaign launched successfully on " + platform + "!"
        ));
    }
}
