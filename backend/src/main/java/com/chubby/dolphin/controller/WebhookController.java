package com.chubby.dolphin.controller;

import com.chubby.dolphin.service.PaymentEventProcessor;
import com.chubby.dolphin.service.RazorpayPaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/billing/razorpay")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final RazorpayPaymentProvider razorpayPaymentProvider;
    private final PaymentEventProcessor paymentEventProcessor;

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestBody String payload,
            @RequestParam("event_id") String eventId,
            @RequestParam("event_type") String eventType) {

        log.info("Received Razorpay webhook: event_id={}, type={}", eventId, eventType);

        // 1. Verify Signature (DA-034)
        if (!razorpayPaymentProvider.verifyWebhook(signature, payload)) {
            log.warn("Invalid webhook signature received for event: {}", eventId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        try {
            // 2. Idempotent Processing (DA-034)
            boolean processed = paymentEventProcessor.processEvent(eventId, "RAZORPAY", eventType, payload);
            if (processed) {
                return ResponseEntity.ok("Event processed successfully");
            } else {
                return ResponseEntity.ok("Event already processed");
            }
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }
    }
}
