package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.LocalApprovalSafetyService;
import com.chubby.dolphin.service.MetaAdsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Meta Ads API Controller — Handles OAuth2 flow, campaign sync,
 * and Meta connection management.
 */
@RestController
@RequestMapping("/api/meta")
@Slf4j
public class MetaController {

    private final MetaAdsService metaAdsService;
    private final SecurityUtils sec;
    private final AccessControlService access;
    private final LocalApprovalSafetyService localApprovalSafetyService;

    public MetaController(MetaAdsService metaAdsService,
                          SecurityUtils sec,
                          AccessControlService access,
                          LocalApprovalSafetyService localApprovalSafetyService) {
        this.metaAdsService = metaAdsService;
        this.sec = sec;
        this.access = access;
        this.localApprovalSafetyService = localApprovalSafetyService;
    }

    /**
     * Step 1: Get the OAuth2 authorization URL — OWNER/ADMIN only.
     */
    @GetMapping("/auth-url")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<?> getAuthUrl() {
        access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
        String accountId = sec.currentWorkspaceId();
        String state = accountId + "|" + System.currentTimeMillis();
        String url = metaAdsService.getAuthorizationUrl(state);
        return ResponseEntity.ok(Map.of("auth_url", url, "state", state));
    }

    /**
     * Step 2: OAuth2 callback — exchange code for token.
     * Called after user grants permissions on Facebook.
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleCallback(@RequestBody Map<String, String> body) {
        access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
        String code = body.get("code");
        String state = body.get("state");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OAuth code is required"));
        }

        // Extract accountId from state
        String accountId = sec.currentWorkspaceId();

        try {
            MetaConnection conn = metaAdsService.exchangeCodeForToken(code, accountId);
            log.info("✅ Meta connected for account: {}", accountId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "connection_id", conn.getId(),
                "ad_account_name", conn.getAdAccountName(),
                "ad_account_id", conn.getMetaAdAccountId(),
                "token_status", conn.getTokenStatus(),
                "auto_manage", conn.isAutoManageEnabled()
            ));
        } catch (Exception e) {
            log.error("Meta OAuth failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to connect Meta account: " + e.getMessage()
            ));
        }
    }

    /**
     * List all Meta connections for the current account.
     */
    @GetMapping("/connections")
    public ResponseEntity<List<MetaConnection>> listConnections() {
        access.requireWorkspacePermission(Permission.INTEGRATION_READ);
        return ResponseEntity.ok(metaAdsService.getConnectionsForAccount(sec.currentWorkspaceId()));
    }

    /**
     * Get active Meta connection status.
     */
    @GetMapping("/status")
    public ResponseEntity<?> connectionStatus() {
        access.requireWorkspacePermission(Permission.INTEGRATION_READ);
        String accountId = sec.currentWorkspaceId();
        return metaAdsService.getActiveConnection(accountId)
                .map(conn -> ResponseEntity.ok(Map.of(
                    "connected", true,
                    "ad_account_name", conn.getAdAccountName() != null ? conn.getAdAccountName() : "",
                    "ad_account_id", conn.getMetaAdAccountId(),
                    "meta_page_id", conn.getMetaPageId() != null ? conn.getMetaPageId() : "",
                    "meta_page_name", conn.getMetaPageName() != null ? conn.getMetaPageName() : "",
                    "token_status", conn.getTokenStatus(),
                    "auto_manage_enabled", conn.isAutoManageEnabled(),
                    "last_sync", conn.getLastSyncAt() != null ? conn.getLastSyncAt().toString() : "never",
                    "max_daily_spend", conn.getMaxDailySpend(),
                    "pause_roas_threshold", conn.getPauseRoasThreshold()
                )))
                .orElse(ResponseEntity.ok(Map.of("connected", false)));
    }

    /**
     * Manually trigger a campaign sync from Meta.
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncCampaigns() {
        access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
        String accountId = sec.currentWorkspaceId();
        return metaAdsService.getActiveConnection(accountId)
                .map(conn -> {
                    var campaigns = metaAdsService.syncCampaigns(conn);
                    return ResponseEntity.ok(Map.of(
                        "synced", campaigns.size(),
                        "message", "Successfully synced " + campaigns.size() + " campaigns from Meta"
                    ));
                })
                .orElse(ResponseEntity.badRequest().body(
                    Map.of("error", "No active Meta connection. Connect your Meta account first.")));
    }

    /**
     * Update Meta connection settings — OWNER/ADMIN only.
     */
    @PutMapping("/connections/{id}/settings")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<?> updateSettings(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
            MetaConnection conn = metaAdsService.updateConnectionSettings(sec.currentWorkspaceId(), id, body);
            return ResponseEntity.ok(Map.of("message", "Settings updated", "connection", conn));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disconnect a Meta account — OWNER/ADMIN only.
     */
    @DeleteMapping("/connections/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<?> disconnect(@PathVariable String id) {
        try {
            access.requireWorkspacePermission(Permission.INTEGRATION_MANAGE);
            metaAdsService.disconnectConnection(sec.currentWorkspaceId(), id);
            return ResponseEntity.ok(Map.of("message", "Meta account disconnected"));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload an ad image file to the active Meta Ad Account library.
     */
    @PostMapping(value = "/upload-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadImage(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        access.requireWorkspacePermission(Permission.FILE_MANAGE);
        String accountId = sec.currentWorkspaceId();
        return metaAdsService.getActiveConnection(accountId)
                .map(conn -> {
                    try {
                        String hash = metaAdsService.uploadAdImage(conn, file.getBytes(), file.getOriginalFilename());
                        return ResponseEntity.ok(Map.of("success", true, "image_hash", hash));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.badRequest().body(Map.of("error", "No active Meta connection connected")));
    }

    /**
     * Launch a full autonomous campaign/ad structure on Meta using AI copy and image hashes.
     */
    @PostMapping("/launch-ad")
    public ResponseEntity<?> launchAd(@RequestBody Map<String, Object> body) {
        access.requireWorkspacePermission(Permission.CAMPAIGN_APPROVE_AI_ACTION);
        String accountId = sec.currentWorkspaceId();
        if (localApprovalSafetyService.shouldRequireApprovalOnly("META_LAUNCH_AD")) {
            localApprovalSafetyService.auditBlockedExecution(
                    accountId,
                    "META_LAUNCH_AD",
                    "MetaAd",
                    null,
                    "Blocked /api/meta/launch-ad before Meta Marketing API call."
            );
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "approval_required", true,
                    "external_execution_allowed", false,
                    "message", localApprovalSafetyService.blockedMessage("Meta launch")
            ));
        }
        return metaAdsService.getActiveConnection(accountId)
                .map(conn -> {
                    try {
                        String campaignName = (String) body.getOrDefault("campaign_name", "AI_Autonomous_Campaign");
                        String adSetName = (String) body.getOrDefault("adset_name", "AI_AdSet");
                        String adName = (String) body.getOrDefault("ad_name", "AI_Ad_Creative");
                        String headline = (String) body.get("headline");
                        String bodyText = (String) body.get("body_text");
                        String imageHash = (String) body.get("image_hash");
                        String targetLink = (String) body.getOrDefault("target_link", "https://chubbydolphin.ai");
                        String pageId = (String) body.get("page_id");
                        double dailyBudget = Double.parseDouble(body.getOrDefault("daily_budget", "500").toString());

                        if (headline == null || bodyText == null || imageHash == null || pageId == null) {
                            return ResponseEntity.badRequest().body(Map.of("error", "headline, body_text, image_hash, and page_id are required"));
                        }

                        String adId = metaAdsService.launchAd(conn, campaignName, adSetName, adName, headline, bodyText, imageHash, targetLink, pageId, dailyBudget);
                        return ResponseEntity.ok(Map.of("success", true, "meta_ad_id", adId));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.badRequest().body(Map.of("error", "No active Meta connection connected")));
    }
}
