package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import com.chubby.dolphin.service.RateLimiterService;
import com.chubby.dolphin.service.WhatsAppService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/webhooks/whatsapp")
@Slf4j
public class WhatsAppController {

    @Value("${meta.app.secret:dolphin_secret}")
    private String appSecret;

    private final WhatsAppService whatsAppService;
    private final WorkspaceConfigRepository configRepo;
    private final AuditLogRepository auditRepo;
    private final RateLimiterService rateLimiter;
    private final MeterRegistry meterRegistry;

    public WhatsAppController(WhatsAppService whatsAppService,
                              WorkspaceConfigRepository configRepo,
                              AuditLogRepository auditRepo,
                              RateLimiterService rateLimiter,
                              MeterRegistry meterRegistry) {
        this.whatsAppService = whatsAppService;
        this.configRepo = configRepo;
        this.auditRepo = auditRepo;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Webhook Verification Endpoint mandated by Meta Developer Portal.
     * Mapped for secure, multi-tenant constant-time comparison checks.
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge,
            HttpServletRequest request) {

        String ipAddress = request.getRemoteAddr();
        log.info("🔔 Received Meta WhatsApp Webhook verification from IP [{}]: mode={}, token=******", ipAddress, mode);

        // 1. Rate Limit the verification endpoint based on caller IP address
        if (!rateLimiter.isAllowed(ipAddress, RateLimiterService.LimitType.GENERAL)) {
            log.warn("⚠️ Rate limit exceeded on webhook verification from IP: {}", ipAddress);
            meterRegistry.counter("chubby_dolphin_whatsapp_webhook_verifications_total", "status", "FAILED").increment();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        // 2. Locate workspace configuration by verify token hash
        Optional<WorkspaceConfig> configOpt = configRepo.findByVerifyToken(token);

        if ("subscribe".equals(mode) && configOpt.isPresent()) {
            WorkspaceConfig config = configOpt.get();
            String dbVerifyToken = config.getWhatsappVerifyToken();

            if (dbVerifyToken != null) {
                // 3. Security: Constant-time comparison to prevent side-channel timing analysis attacks
                boolean matches = MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.UTF_8),
                        dbVerifyToken.getBytes(StandardCharsets.UTF_8)
                );

                if (matches) {
                    // Record SUCCESS metric
                    meterRegistry.counter("chubby_dolphin_whatsapp_webhook_verifications_total", "status", "SUCCESS").increment();

                    // Generate Audit Log
                    AuditLog audit = new AuditLog();
                    audit.setUserEmail("system-meta-webhook");
                    audit.setAction("VERIFY_WHATSAPP_WEBHOOK");
                    audit.setResourceType("WORKSPACE");
                    audit.setResourceId(config.getWorkspaceId());
                    audit.setDetails("Dynamic WhatsApp Webhook successfully verified for tenant workspace.");
                    audit.setIpAddress(ipAddress);
                    audit.setTimestamp(LocalDateTime.now());
                    auditRepo.save(audit);

                    log.info("✅ Meta WhatsApp Webhook successfully verified for workspace ID: {}", config.getWorkspaceId());
                    return ResponseEntity.ok(challenge);
                }
            }
        }

        // 4. Verification Failed
        meterRegistry.counter("chubby_dolphin_whatsapp_webhook_verifications_total", "status", "FAILED").increment();

        AuditLog failedAudit = new AuditLog();
        failedAudit.setUserEmail("system-meta-webhook");
        failedAudit.setAction("VERIFY_WHATSAPP_WEBHOOK_FAILED");
        failedAudit.setResourceType("IP_ADDRESS");
        failedAudit.setResourceId(ipAddress);
        failedAudit.setDetails("WhatsApp Webhook verification rejected due to unauthorized or mismatched verify token: " + token);
        failedAudit.setIpAddress(ipAddress);
        failedAudit.setTimestamp(LocalDateTime.now());
        auditRepo.save(failedAudit);

        log.warn("❌ Webhook verification failed due to unauthorized verify_token.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Payload Ingestion Webhook for delivery reports and user replies.
     */
    @PostMapping
    public ResponseEntity<Void> receiveWebhookPayload(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            HttpServletRequest request) {
        
        String ipAddress = request.getRemoteAddr();
        
        // Rate limit: 100 requests/minute/IP
        if (!rateLimiter.isAllowed(ipAddress, RateLimiterService.LimitType.WEBHOOK)) {
            log.warn("⚠️ Rate limit exceeded on WhatsApp Webhook from IP: {}", ipAddress);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        log.info("📩 Ingesting incoming WhatsApp event payload...");
        
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("❌ Webhook rejected: Missing mandatory X-Hub-Signature-256 header from IP: {}", ipAddress);
            saveFailedSignatureAudit(ipAddress, "MISSING");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        if (!isValidSignature(payload, signatureHeader)) {
            log.warn("❌ Webhook HMAC-SHA256 signature validation failed from IP: {}. Rejecting webhook request.", ipAddress);
            saveFailedSignatureAudit(ipAddress, "INVALID");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("🔒 Incoming webhook HMAC validation successful via X-Hub-Signature-256.");
        whatsAppService.receiveWebhook(payload);
        return ResponseEntity.ok().build();
    }

    private void saveFailedSignatureAudit(String ipAddress, String reason) {
        AuditLog audit = new AuditLog();
        audit.setUserEmail("system-meta-webhook");
        audit.setEventType("WEBHOOK_VERIFICATION_FAILED");
        audit.setAction("VERIFY_WHATSAPP_WEBHOOK_FAILED");
        audit.setResourceType("IP_ADDRESS");
        audit.setResourceId(ipAddress);
        audit.setDetails("WhatsApp Webhook payload signature check failed. Reason: " + reason);
        audit.setIpAddress(ipAddress);
        audit.setTimestamp(LocalDateTime.now());
        audit.setActorType("SYSTEM");
        audit.setActorId("system-meta-webhook");
        auditRepo.save(audit);
    }

    private boolean isValidSignature(String payload, String signatureHeader) {
        if (!signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String clientSig = signatureHeader.substring(7);
        try {
            String key = (appSecret == null || appSecret.isBlank()) ? "dolphin_secret" : appSecret;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String calculatedSig = hexString.toString();
            return MessageDigest.isEqual(
                    clientSig.getBytes(StandardCharsets.UTF_8),
                    calculatedSig.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying X-Hub-Signature-256: ", e);
            return false;
        }
    }
}
