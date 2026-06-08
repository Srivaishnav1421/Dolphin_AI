package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/whatsapp")
@Slf4j
public class WhatsAppSettingsController {

    private final WorkspaceConfigRepository configRepo;
    private final SecurityUtils sec;
    private final RateLimiterService rateLimiter;

    public WhatsAppSettingsController(WorkspaceConfigRepository configRepo, SecurityUtils sec, RateLimiterService rateLimiter) {
        this.configRepo = configRepo;
        this.sec = sec;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Generate or update the WhatsApp webhook verification token for a workspace.
     */
    @PostMapping("/webhook-token")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<?> generateWebhookToken(@RequestBody Map<String, Object> body) {
        String currentEmail = sec.currentEmail();
        
        // 1. Security: Enforce rate limiting to prevent spamming secure generation endpoints
        if (!rateLimiter.isAllowed(currentEmail, RateLimiterService.LimitType.GENERAL)) {
            log.warn("⚠️ Rate limit exceeded on webhook token generation for user: {}", currentEmail);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many requests. Please wait a minute and try again."));
        }

        String workspaceId = (String) body.get("workspaceId");
        Boolean generate = (Boolean) body.get("generate");

        if (workspaceId == null || workspaceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "workspaceId is required"));
        }

        // 2. Validate tenant access security
        String activeWorkspaceId = sec.currentAccountId();
        if (!workspaceId.equals(activeWorkspaceId)) {
            log.warn("🚨 Security Warning: User tried to modify settings for unauthorized workspace: {}", workspaceId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied to the requested workspace context."));
        }

        WorkspaceConfig config = configRepo.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    WorkspaceConfig c = new WorkspaceConfig();
                    c.setWorkspaceId(workspaceId);
                    c.setCreatedAt(LocalDateTime.now());
                    return c;
                });

        String rawToken = null;
        if (Boolean.TRUE.equals(generate)) {
            // Generate cryptographically secure verify token
            SecureRandom sr = new SecureRandom();
            byte[] bytes = new byte[24];
            sr.nextBytes(bytes);
            rawToken = "CD_verify_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            // Set encrypted verify token (JPA converter will handle transparent AES-256 conversion)
            config.setWhatsappVerifyToken(rawToken);

            // Compute SHA-256 hash for fast secure database lookup index
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : hashBytes) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                config.setWhatsappVerifyTokenHash(hexString.toString());
            } catch (Exception e) {
                log.error("Failed to compute SHA-256 hash on verification token", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Cryptographic hashing failed."));
            }

            config.setWhatsappWebhookEnabled(true);
            config.setUpdatedAt(LocalDateTime.now());
            configRepo.save(config);

            log.info("🔐 Secure random WhatsApp Webhook verification token freshly generated for workspace: {}", workspaceId);
        } else {
            rawToken = config.getWhatsappVerifyToken();
        }

        if (rawToken == null) {
            return ResponseEntity.ok(Map.of(
                    "workspaceId", workspaceId,
                    "maskedToken", "No token generated yet.",
                    "webhookEnabled", config.getWhatsappWebhookEnabled() != null && config.getWhatsappWebhookEnabled()
            ));
        }

        // Mask the token string for transmission safety
        String maskedToken = "CD_verify_******************" + rawToken.substring(rawToken.length() - 4);

        Map<String, Object> response = new HashMap<>();
        response.put("workspaceId", workspaceId);
        response.put("maskedToken", maskedToken);
        response.put("webhookEnabled", config.getWhatsappWebhookEnabled() != null && config.getWhatsappWebhookEnabled());
        if (rawToken != null && Boolean.TRUE.equals(generate)) {
            // Return unmasked token ONLY once during generation so the client can save it
            response.put("unmaskedToken", rawToken);
        }

        return ResponseEntity.ok(response);
    }
}
