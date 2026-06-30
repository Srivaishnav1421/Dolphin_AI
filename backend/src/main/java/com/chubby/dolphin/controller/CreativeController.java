package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.audit.AuditLogService;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.CreativeAIService;
import com.chubby.dolphin.service.BusinessLlmFacadeService;
import com.chubby.dolphin.repository.AdCreativeRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Creative AI Controller — Endpoints for AI-powered ad creative generation.
 */
@RestController
@RequestMapping("/api/creatives")
@Slf4j
public class CreativeController {
    private static final Set<String> ALLOWED_PLATFORMS = Set.of(
            "FACEBOOK_FEED", "INSTAGRAM_FEED", "INSTAGRAM_STORY", "REELS", "LINKEDIN", "X_TWITTER", "INSTAGRAM_POST"
    );
    private static final Set<String> ALLOWED_STATUSES = Set.of("DRAFT", "REVIEW", "APPROVED", "ACTIVE", "PAUSED", "ARCHIVED");
    private static final Set<String> ALLOWED_QUALITY = Set.of("FAST", "BALANCED", "PREMIUM");
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("en", "hi", "ta", "te", "kn", "bn", "mr");

    private final CreativeAIService creativeService;
    private final SecurityUtils sec;
    private final AccessControlService access;
    private final AuditLogService auditLogService;
    private final AdCreativeRepository creativeRepo;
    private final CampaignRepository campaignRepo;

    public CreativeController(CreativeAIService creativeService, SecurityUtils sec,
                              AccessControlService access, AuditLogService auditLogService,
                              AdCreativeRepository creativeRepo, CampaignRepository campaignRepo) {
        this.creativeService = creativeService;
        this.sec = sec;
        this.access = access;
        this.auditLogService = auditLogService;
        this.creativeRepo = creativeRepo;
        this.campaignRepo = campaignRepo;
    }

    /** List all creatives, optionally filtered by status */
    @GetMapping
    public ResponseEntity<List<AdCreative>> list(@RequestParam(required = false) String status) {
        access.requireWorkspacePermission(Permission.CREATIVE_READ);
        String workspaceId = sec.currentWorkspaceId();
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(creativeRepo.findByWorkspaceIdAndStatus(workspaceId, status.toUpperCase()));
        }
        return ResponseEntity.ok(creativeRepo.findByWorkspaceId(workspaceId));
    }

    /** Get creatives for a specific campaign */
    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<AdCreative>> campaignCreatives(@PathVariable String campaignId) {
        access.requireWorkspacePermission(Permission.CREATIVE_READ);
        String workspaceId = sec.currentWorkspaceId();
        Optional<com.chubby.dolphin.entity.Campaign> campOpt = campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId);
        if (campOpt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(creativeService.getCampaignCreatives(campaignId, workspaceId));
    }

    /**
     * Generate AI ad copy variations.
     * Required body: product, audience, tone, platform
     * Optional body: campaignId
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.CREATIVE_GENERATE);
        String workspaceId = sec.currentWorkspaceId();
        String product  = clean(body.getOrDefault("product", ""), 1200);
        String audience = clean(body.getOrDefault("audience", ""), 500);
        String tone     = clean(body.getOrDefault("tone", "professional"), 80);
        String platform = normalizePlatform(clean(body.getOrDefault("platform", "FACEBOOK_FEED"), 40));
        String languageCode = normalizeLanguage(clean(body.getOrDefault("language_code", "en"), 8));
        String qualityTier = clean(body.getOrDefault("quality_tier", "BALANCED"), 20).toUpperCase();
        String campaignId = body.get("campaign_id");

        if (product.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product/service description is required"));
        }
        if (!ALLOWED_PLATFORMS.contains(platform)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported creative platform"));
        }
        if (!ALLOWED_LANGUAGES.contains(languageCode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language code"));
        }
        if (!ALLOWED_QUALITY.contains(qualityTier)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported quality tier"));
        }

        if (campaignId != null && !campaignId.isBlank()) {
            Optional<com.chubby.dolphin.entity.Campaign> campOpt = campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId);
            if (campOpt.isEmpty()) return ResponseEntity.notFound().build();
        }

        List<AdCreative> creatives;
        try {
            creatives = creativeService.generateAdCopy(
                workspaceId, campaignId, product, audience, tone, platform, languageCode, qualityTier
            );
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", e.getMessage(),
                "next_step", "Connect a healthy AI provider in Integrations or start Ollama on the configured URL."
            ));
        }

        auditCreative("CREATIVE_GENERATED", "AdCreative", campaignId != null ? campaignId : workspaceId,
                "generated=" + creatives.size() + "; platform=" + platform + "; quality=" + qualityTier);

        return ResponseEntity.ok(Map.of(
            "generated", creatives.size(),
            "creatives", creatives
        ));
    }

    /** Suggest A/B tests for a campaign's creatives */
    @PostMapping("/campaign/{campaignId}/ab-suggest")
    public ResponseEntity<?> suggestABTests(@PathVariable String campaignId) {
        access.requireWorkspacePermission(Permission.CREATIVE_GENERATE);
        String workspaceId = sec.currentWorkspaceId();
        Optional<com.chubby.dolphin.entity.Campaign> campOpt = campaignRepo.findByIdAndWorkspaceId(campaignId, workspaceId);
        if (campOpt.isEmpty()) return ResponseEntity.notFound().build();
        BusinessLlmFacadeService.LlmResponse response = creativeService.suggestABTests(campaignId, workspaceId);
        auditCreative("CREATIVE_AB_SUGGESTED", "Campaign", campaignId, "provider=" + response.provider());
        return ResponseEntity.ok(Map.of(
            "suggestions", response.text(),
            "provider", response.provider()
        ));
    }

    /** Rewrite a creative for a different platform */
    @PostMapping("/{id}/rewrite")
    public ResponseEntity<?> rewrite(@PathVariable String id, @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.CREATIVE_GENERATE);
        String workspaceId = sec.currentWorkspaceId();
        Optional<AdCreative> opt = creativeRepo.findByIdAndWorkspaceId(id, workspaceId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        String platform = normalizePlatform(clean(body.getOrDefault("platform", "INSTAGRAM_STORY"), 40));
        if (!ALLOWED_PLATFORMS.contains(platform)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported creative platform"));
        }
        AdCreative rewritten;
        try {
            rewritten = creativeService.rewriteForPlatform(id, workspaceId, platform);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", e.getMessage(),
                "next_step", "Connect a healthy AI provider in Integrations or start Ollama on the configured URL."
            ));
        }
        auditCreative("CREATIVE_REWRITTEN", "AdCreative", id,
                "newCreativeId=" + rewritten.getId() + "; platform=" + platform);
        return ResponseEntity.ok(rewritten);
    }

    /** Update creative status */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.CREATIVE_UPDATE);
        String workspaceId = sec.currentWorkspaceId();
        Optional<AdCreative> opt = creativeRepo.findByIdAndWorkspaceId(id, workspaceId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().body(Map.of("error", "Status required"));
        status = clean(status, 24).toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported creative status"));
        }
        AdCreative updated = creativeService.updateStatus(id, workspaceId, status);
        auditCreative("CREATIVE_STATUS_UPDATED", "AdCreative", id, "status=" + status);
        return ResponseEntity.ok(updated);
    }

    /** Publishing is intentionally not faked. Connect a real ad publisher before enabling this action. */
    @PostMapping("/{id}/launch")
    public ResponseEntity<?> launch(@PathVariable String id, @RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_APPROVE_AI_ACTION);
        Optional<AdCreative> opt = creativeRepo.findByIdAndWorkspaceId(id, sec.currentWorkspaceId());
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        auditCreative("CREATIVE_LAUNCH_ATTEMPTED", "AdCreative", id, "publishing_not_enabled");
        return ResponseEntity.status(424).body(Map.of(
            "success", false,
            "creativeId", id,
            "message", "Publishing is not enabled yet. Approve the creative, then launch it from a connected ads account."
        ));
    }

    private String clean(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizePlatform(String platform) {
        String normalized = platform == null ? "FACEBOOK_FEED" : platform.trim().toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "META", "META_AD", "META_ADS", "FACEBOOK", "FB", "FB_FEED" -> "FACEBOOK_FEED";
            case "IG", "INSTAGRAM", "IG_FEED" -> "INSTAGRAM_FEED";
            case "IG_STORY", "STORY", "STORIES" -> "INSTAGRAM_STORY";
            case "REEL", "INSTAGRAM_REELS" -> "REELS";
            case "TWITTER", "X" -> "X_TWITTER";
            default -> normalized;
        };
    }

    private String normalizeLanguage(String languageCode) {
        String normalized = languageCode == null ? "en" : languageCode.trim().toLowerCase().replace('_', '-');
        if (normalized.startsWith("en")) {
            return "en";
        }
        int regionSeparator = normalized.indexOf('-');
        return regionSeparator > 0 ? normalized.substring(0, regionSeparator) : normalized;
    }

    private void auditCreative(String action, String entityType, String entityId, String details) {
        auditLogService.record(access.currentUser(), access.currentUser().getOrganization(), sec.currentWorkspaceId(),
                action, entityType, entityId, auditLogService.redact(details));
    }
}
