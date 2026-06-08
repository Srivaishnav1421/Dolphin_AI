package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.AdvantageIntelligenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/advantage-intel")
@Slf4j
public class AdvantageIntelligenceController {

    private final AdvantageIntelligenceService intelligenceService;
    private final SecurityUtils sec;

    public AdvantageIntelligenceController(AdvantageIntelligenceService intelligenceService,
                                            SecurityUtils sec) {
        this.intelligenceService = intelligenceService;
        this.sec = sec;
    }

    /**
     * Advantage+ Intelligence Layer: Generates 3x3 Dynamic Copy permutations (Hooks x Bodies x CTAs)
     * and scores their expected CTR, returning a complete testing grid.
     */
    @PostMapping("/generate-matrix")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<List<AdCreative>> generateTestingGrid(@RequestBody Map<String, String> body) {
        String campaignId = body.get("campaign_id");
        String productDesc = body.get("product_description");
        String targetAudience = body.get("target_audience");

        if (campaignId == null || productDesc == null || targetAudience == null ||
                campaignId.isBlank() || productDesc.isBlank() || targetAudience.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<AdCreative> grid = intelligenceService.generateMultiVariateGrid(
                sec.currentAccountId(), campaignId, productDesc, targetAudience
        );

        return ResponseEntity.ok(grid);
    }
}
