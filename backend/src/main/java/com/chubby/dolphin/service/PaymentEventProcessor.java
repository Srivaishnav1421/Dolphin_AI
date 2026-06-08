package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.PaymentEvent;
import com.chubby.dolphin.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProcessor {

    private final PaymentEventRepository paymentEventRepository;
    private final WalletService walletService;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final FinancialEventService financialEventService;

    @Transactional
    public boolean processEvent(String eventId, String provider, String eventType, String payload) {
        // Idempotency: check if already processed (DA-034)
        Optional<PaymentEvent> existing = paymentEventRepository.findById(eventId);
        if (existing.isPresent()) {
            log.warn("Payment event {} has already been processed. Skipping.", eventId);
            return false;
        }

        // Generate SHA-256 hash of payload for auditing integrity
        String payloadHash = hashPayload(payload);

        PaymentEvent event = PaymentEvent.builder()
                .paymentEventId(eventId)
                .provider(provider)
                .eventType(eventType)
                .payloadHash(payloadHash)
                .status("PENDING")
                .build();
        paymentEventRepository.saveAndFlush(event);

        try {
            log.info("Processing webhook event: id={}, type={}", eventId, eventType);
            dispatchBillingAction(eventType, payload);

            event.setStatus("PROCESSED");
            event.setProcessedAt(LocalDateTime.now());
            paymentEventRepository.save(event);
            return true;
        } catch (Exception e) {
            log.error("Failed to process payment event: id={}, error={}", eventId, e.getMessage(), e);
            event.setStatus("FAILED");
            paymentEventRepository.save(event);
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    private void dispatchBillingAction(String eventType, String payload) {
        // In a real application, parse JSON fields like workspace_id, amount, sub_id
        // Since we are mocking the physical provider body parsing, we simulate standard action dispatches:
        
        String mockWorkspaceId = "workspace_prod_default";
        
        switch (eventType) {
            case "payment.captured":
                log.info("Handling payment.captured for workspace {}", mockWorkspaceId);
                double amount = 2000.00;
                walletService.creditWallet(mockWorkspaceId, amount, "TOPUP", "pay_" + UUID(), "RAZORPAY_CAPTURE");
                financialEventService.recordFinancialEvent(mockWorkspaceId, "REVENUE", amount, "pay_" + UUID());
                break;

            case "payment.failed":
                log.warn("Handling payment.failed event for workspace {}", mockWorkspaceId);
                break;

            case "subscription.activated":
            case "subscription.charged":
                log.info("Handling subscription billing status updates for workspace {}", mockWorkspaceId);
                subscriptionLifecycleService.activateSubscription(mockWorkspaceId);
                break;

            case "subscription.completed":
            case "subscription.cancelled":
                log.info("Handling subscription cancellation or end of cycle for workspace {}", mockWorkspaceId);
                subscriptionLifecycleService.cancelSubscriptionImmediately(mockWorkspaceId);
                break;

            case "refund.processed":
                log.info("Handling refund.processed for workspace {}", mockWorkspaceId);
                double refundAmount = 500.00;
                walletService.debitWallet(mockWorkspaceId, refundAmount, "REFUND", "ref_" + UUID(), "RAZORPAY_REFUND");
                financialEventService.recordFinancialEvent(mockWorkspaceId, "REFUND", -refundAmount, "ref_" + UUID());
                break;

            default:
                log.info("Unhandled event type: {}", eventType);
                break;
        }
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "hash_error";
        }
    }

    private String UUID() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
