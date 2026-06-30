package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.PaymentEventProcessor;
import com.chubby.dolphin.service.RazorpayPaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Unified Razorpay Webhook Controller (DA-034 / DA-051).
 *
 * Security model:
 * - No JWT required (Razorpay calls this endpoint directly)
 * - Signature verified via HMAC-SHA256 against raw request body
 * - Idempotency enforced by payment_event_id uniqueness in PaymentEventProcessor
 * - Replay protection: duplicate payment_event_id is rejected
 *
 * DA-051: 5-minute timestamp check REMOVED — idempotency is the protection layer.
 */
@RestController
@RequestMapping("/api/billing/razorpay")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final RazorpayPaymentProvider razorpayPaymentProvider;
    private final PaymentEventProcessor paymentEventProcessor;
    private final ObjectMapper objectMapper;

    /**
     * Razorpay webhook endpoint.
     * Receives raw POST body from Razorpay, validates HMAC-SHA256 signature,
     * parses the event type and delegates to PaymentEventProcessor.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        log.info("📬 Razorpay webhook received. Payload size: {} bytes", rawPayload.length());

        // 1. Signature verification (DA-034)
        if (signature == null || signature.isBlank()) {
            log.warn("🚫 Webhook rejected: missing X-Razorpay-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature");
        }

        if (!razorpayPaymentProvider.verifyWebhook(signature, rawPayload)) {
            log.warn("🚫 Webhook rejected: invalid HMAC-SHA256 signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        // 2. Parse webhook event metadata from JSON body
        String eventId;
        String eventType;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            eventId = root.path("id").asText(null);
            eventType = root.path("event").asText(null);

            if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
                log.warn("⚠️ Webhook body missing 'id' or 'event' field. Payload: {}", rawPayload.substring(0, Math.min(200, rawPayload.length())));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing event id or event type in payload");
            }
        } catch (Exception e) {
            log.error("❌ Failed to parse Razorpay webhook JSON body", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Malformed JSON payload");
        }

        log.info("📨 Processing Razorpay event: id={}, type={}", eventId, eventType);

        // 3. Idempotent, transactional event processing (DA-034 / DA-051)
        try {
            boolean processed = paymentEventProcessor.processEvent(eventId, "RAZORPAY", eventType, rawPayload);
            if (processed) {
                log.info("✅ Webhook event processed: id={}, type={}", eventId, eventType);
                return ResponseEntity.ok("Event processed successfully");
            } else {
                log.info("⏩ Webhook event already processed (idempotent skip): id={}", eventId);
                return ResponseEntity.ok("Event already processed");
            }
        } catch (Exception e) {
            log.error("❌ Webhook processing error: id={}, error={}", eventId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }
    }
}
