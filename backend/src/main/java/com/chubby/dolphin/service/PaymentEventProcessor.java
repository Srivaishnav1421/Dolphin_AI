package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.PaymentEvent;
import com.chubby.dolphin.repository.PaymentEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PaymentEventProcessor — Transactional, duplicate-safe, replay-safe webhook event handler.
 *
 * Idempotency model (DA-051):
 * - Payment event ID is the primary key. Duplicate event_ids are rejected silently.
 * - Payload SHA-256 hash is stored for audit integrity.
 * - Status transitions: PENDING → PROCESSED or FAILED.
 *
 * Supported events:
 *   payment.captured    → Credit wallet + FinancialEvent + GST Invoice
 *   payment.failed      → Set PAST_DUE
 *   subscription.activated → Set ACTIVE
 *   subscription.charged   → Set ACTIVE + credit wallet
 *   subscription.cancelled → Set SUSPENDED
 *   refund.processed    → Debit wallet + FinancialEvent
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProcessor {

    private final PaymentEventRepository paymentEventRepository;
    private final WalletService walletService;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final FinancialEventService financialEventService;
    private final GstInvoiceService gstInvoiceService;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean processEvent(String eventId, String provider, String eventType, String payload) {
        // Idempotency guard — reject duplicates immediately (DA-051)
        Optional<PaymentEvent> existing = paymentEventRepository.findById(eventId);
        if (existing.isPresent()) {
            log.warn("⏩ Payment event {} already processed (status={}). Skipping.", eventId, existing.get().getStatus());
            return false;
        }

        // Compute SHA-256 hash of payload for audit integrity
        String payloadHash = hashPayload(payload);

        // Persist the event record immediately before processing (PENDING state)
        // This prevents a concurrent duplicate from slipping through before we complete
        PaymentEvent event = PaymentEvent.builder()
                .paymentEventId(eventId)
                .provider(provider)
                .eventType(eventType)
                .payloadHash(payloadHash)
                .status("PENDING")
                .build();
        paymentEventRepository.saveAndFlush(event);

        try {
            log.info("⚙️ Dispatching billing action: id={}, type={}", eventId, eventType);
            dispatchBillingAction(eventType, payload);

            event.setStatus("PROCESSED");
            event.setProcessedAt(LocalDateTime.now());
            paymentEventRepository.save(event);
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to process payment event: id={}, error={}", eventId, e.getMessage(), e);
            event.setStatus("FAILED");
            paymentEventRepository.save(event);
            // Re-throw to trigger HTTP 500 so Razorpay can retry
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    // ── Event Dispatch ────────────────────────────────────────────────────────

    private void dispatchBillingAction(String eventType, String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode payloadNode = root.path("payload");

        switch (eventType) {

            case "payment.captured" -> handlePaymentCaptured(root, payloadNode);
            case "payment.failed"   -> handlePaymentFailed(root, payloadNode);
            case "subscription.activated" -> handleSubscriptionActivated(root, payloadNode);
            case "subscription.charged"   -> handleSubscriptionCharged(root, payloadNode);
            case "subscription.cancelled",
                 "subscription.completed" -> handleSubscriptionCancelled(root, payloadNode);
            case "refund.processed" -> handleRefundProcessed(root, payloadNode);
            default -> log.info("⚠️ Unhandled Razorpay event type: {}", eventType);
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handlePaymentCaptured(JsonNode root, JsonNode payloadNode) {
        String workspaceId = extractWorkspaceId(root, payloadNode);
        String paymentId   = payloadNode.path("payment").path("entity").path("id").asText("pay_unknown");
        double amount      = payloadNode.path("payment").path("entity").path("amount").asDouble(0) / 100.0; // paise to INR

        log.info("💰 payment.captured: workspace={}, paymentId={}, amount=₹{}", workspaceId, paymentId, amount);

        // 1. Credit wallet
        walletService.creditWallet(workspaceId, amount, "SUBSCRIPTION_PAYMENT", paymentId, "RAZORPAY_CAPTURE");

        // 2. Record financial event
        financialEventService.recordFinancialEvent(workspaceId, "REVENUE", amount, paymentId);

        // 3. Generate GST invoice
        gstInvoiceService.createInvoice(workspaceId, paymentId, amount);

        log.info("✅ payment.captured fully handled for workspace={}", workspaceId);
    }

    private void handlePaymentFailed(JsonNode root, JsonNode payloadNode) {
        String workspaceId = extractWorkspaceId(root, payloadNode);
        String paymentId   = payloadNode.path("payment").path("entity").path("id").asText("pay_unknown");
        log.warn("⚠️ payment.failed: workspace={}, paymentId={} → transitioning to PAST_DUE", workspaceId, paymentId);
        subscriptionLifecycleService.markPastDue(workspaceId);
    }

    private void handleSubscriptionActivated(JsonNode root, JsonNode payloadNode) {
        String workspaceId = extractWorkspaceId(root, payloadNode);
        String subscriptionId = payloadNode.path("subscription").path("entity").path("id").asText("sub_unknown");
        log.info("✅ subscription.activated: workspace={}, subscriptionId={}", workspaceId, subscriptionId);
        subscriptionLifecycleService.activateSubscription(workspaceId);
    }

    private void handleSubscriptionCharged(JsonNode root, JsonNode payloadNode) {
        String workspaceId = extractWorkspaceId(root, payloadNode);
        String subscriptionId = payloadNode.path("subscription").path("entity").path("id").asText("sub_unknown");
        String paymentId      = payloadNode.path("payment").path("entity").path("id").asText("pay_unknown");
        double amount         = payloadNode.path("payment").path("entity").path("amount").asDouble(0) / 100.0;

        log.info("💳 subscription.charged: workspace={}, amount=₹{}", workspaceId, amount);

        // Keep subscription ACTIVE on successful charge
        subscriptionLifecycleService.activateSubscription(workspaceId);

        // Credit wallet for the subscription period payment
        if (amount > 0) {
            walletService.creditWallet(workspaceId, amount, "SUBSCRIPTION_RENEWAL", paymentId, "RAZORPAY_SUBSCRIPTION");
            financialEventService.recordFinancialEvent(workspaceId, "REVENUE", amount, paymentId);
            gstInvoiceService.createInvoice(workspaceId, paymentId, amount);
        }
    }

    private void handleSubscriptionCancelled(JsonNode root, JsonNode payloadNode) {
        String workspaceId = extractWorkspaceId(root, payloadNode);
        String subscriptionId = payloadNode.path("subscription").path("entity").path("id").asText("sub_unknown");
        log.warn("🔴 subscription.cancelled: workspace={}, subscriptionId={} → SUSPENDED", workspaceId, subscriptionId);
        subscriptionLifecycleService.cancelSubscriptionImmediately(workspaceId);
    }

    private void handleRefundProcessed(JsonNode root, JsonNode payloadNode) {
        String workspaceId = extractWorkspaceId(root, payloadNode);
        String refundId    = payloadNode.path("refund").path("entity").path("id").asText("rfnd_unknown");
        double amount      = payloadNode.path("refund").path("entity").path("amount").asDouble(0) / 100.0;

        log.info("↩️ refund.processed: workspace={}, refundId={}, amount=₹{}", workspaceId, refundId, amount);

        // Debit wallet — refund reduces the credited balance
        walletService.debitWallet(workspaceId, amount, "REFUND", refundId, "RAZORPAY_REFUND");

        // Record refund as negative financial event
        financialEventService.recordFinancialEvent(workspaceId, "REFUND", -amount, refundId);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Extract workspace_id from Razorpay webhook payload.
     * Razorpay allows custom notes on subscriptions; we use notes.workspace_id.
     * Billing must fail closed when the tenant cannot be resolved.
     */
    private String extractWorkspaceId(JsonNode root, JsonNode payloadNode) {
        // Try notes.workspace_id on payment entity
        String fromPaymentNotes = payloadNode.path("payment").path("entity")
                .path("notes").path("workspace_id").asText(null);
        if (fromPaymentNotes != null && !fromPaymentNotes.isBlank()) {
            return fromPaymentNotes;
        }
        // Try notes.workspace_id on subscription entity
        String fromSubNotes = payloadNode.path("subscription").path("entity")
                .path("notes").path("workspace_id").asText(null);
        if (fromSubNotes != null && !fromSubNotes.isBlank()) {
            return fromSubNotes;
        }
        // Try top-level account_id (Razorpay linked account)
        String accountId = root.path("account_id").asText(null);
        if (accountId != null && !accountId.isBlank()) {
            return accountId;
        }
        throw new IllegalArgumentException("Missing workspace_id in Razorpay webhook payload.");
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to hash payload", e);
            return "hash_error";
        }
    }
}
