package com.chubby.dolphin.service;

import com.razorpay.Customer;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * RazorpayPaymentProvider — India-first Razorpay payment gateway integration.
 *
 * All secrets loaded from environment variables only (no hardcoded credentials).
 * Webhook signature verification uses real HMAC-SHA256 computation.
 */
@Service("razorpayPaymentProvider")
@Slf4j
public class RazorpayPaymentProvider implements PaymentProvider {

    @Value("${razorpay.key.id:}")
    private String keyId;

    @Value("${razorpay.key.secret:}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    // ── Customer Management ───────────────────────────────────────────────────

    @Override
    public Map<String, Object> createCustomer(String workspaceId, String email, String name) {
        log.info("📋 Razorpay: createCustomer workspace={}, email={}", workspaceId, email);
        try {
            JSONObject payload = new JSONObject();
            payload.put("name", name);
            payload.put("email", email);
            payload.put("notes", new JSONObject(Map.of("workspace_id", workspaceId)));
            Customer customer = client().customers.create(payload);

            Map<String, Object> response = entityToMap(customer.toJson());
            response.put("customer_id", customer.get("id"));
            response.put("provider", "RAZORPAY");
            return response;
        } catch (RazorpayException e) {
            throw new IllegalStateException("Razorpay customer creation failed: " + e.getMessage(), e);
        }
    }

    // ── Subscription Management ───────────────────────────────────────────────

    @Override
    public Map<String, Object> createSubscription(String workspaceId, String planId, int seats) {
        log.info("📋 Razorpay: createSubscription workspace={}, planId={}, seats={}", workspaceId, planId, seats);
        try {
            JSONObject payload = new JSONObject();
            payload.put("plan_id", planId);
            payload.put("total_count", 120);
            payload.put("quantity", Math.max(1, seats));
            payload.put("customer_notify", 1);
            payload.put("notes", new JSONObject(Map.of("workspace_id", workspaceId)));
            Subscription subscription = client().subscriptions.create(payload);

            Map<String, Object> response = entityToMap(subscription.toJson());
            response.put("subscription_id", subscription.get("id"));
            response.put("provider", "RAZORPAY");
            response.put("currency", currency);
            return response;
        } catch (RazorpayException e) {
            throw new IllegalStateException("Razorpay subscription creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean cancelSubscription(String workspaceId, String subscriptionId) {
        log.info("🔴 Razorpay: cancelSubscription workspace={}, subscriptionId={}", workspaceId, subscriptionId);
        try {
            JSONObject payload = new JSONObject();
            payload.put("cancel_at_cycle_end", false);
            client().subscriptions.cancel(subscriptionId, payload);
            return true;
        } catch (RazorpayException e) {
            throw new IllegalStateException("Razorpay subscription cancellation failed: " + e.getMessage(), e);
        }
    }

    // ── Payment Operations ────────────────────────────────────────────────────

    @Override
    public Map<String, Object> createPayment(String workspaceId, double amount, String currencyOverride) {
        log.info("💳 Razorpay: createPayment workspace={}, amount=₹{}", workspaceId, amount);
        // Convert INR to paise (Razorpay uses smallest currency unit)
        long amountPaise = (long) (amount * 100);
        try {
            JSONObject payload = new JSONObject();
            payload.put("amount", amountPaise);
            payload.put("currency", normalizeCurrency(currencyOverride));
            payload.put("receipt", workspaceId + "-" + System.currentTimeMillis());
            payload.put("payment_capture", 1);
            payload.put("notes", new JSONObject(Map.of("workspace_id", workspaceId)));
            Order order = client().orders.create(payload);

            Map<String, Object> response = entityToMap(order.toJson());
            response.put("order_id", order.get("id"));
            response.put("amount_inr", amount);
            response.put("provider", "RAZORPAY");
            return response;
        } catch (RazorpayException e) {
            throw new IllegalStateException("Razorpay order creation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean refundPayment(String workspaceId, String referenceId, double amount) {
        log.info("↩️ Razorpay: refundPayment workspace={}, paymentId={}, amount=₹{}", workspaceId, referenceId, amount);
        try {
            JSONObject payload = new JSONObject();
            payload.put("amount", (long) (amount * 100));
            payload.put("notes", new JSONObject(Map.of("workspace_id", workspaceId)));
            Refund refund = client().payments.refund(referenceId, payload);
            return refund != null && refund.has("id");
        } catch (RazorpayException e) {
            throw new IllegalStateException("Razorpay refund failed: " + e.getMessage(), e);
        }
    }

    // ── Webhook Signature Verification ───────────────────────────────────────

    /**
     * Verifies Razorpay webhook signature using HMAC-SHA256.
     * Razorpay computes: HMAC-SHA256(rawPayload, webhookSecret) and sends hex in X-Razorpay-Signature.
     *
     * @param signature  The value of X-Razorpay-Signature header
     * @param rawPayload The raw POST body string (must NOT be parsed/modified before this call)
     * @return true if signature matches, false otherwise
     */
    @Override
    public boolean verifyWebhook(String signature, String rawPayload) {
        if (signature == null || rawPayload == null) {
            log.warn("🚫 Webhook verification failed: null signature or payload");
            return false;
        }

        String secret = resolveWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.error("❌ RAZORPAY_WEBHOOK_SECRET is not configured. Cannot verify webhook.");
            return false;
        }

        try {
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);
            byte[] hash = sha256HMAC.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            boolean valid = MessageDigest.isEqual(
                    hexString.toString().getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
            if (!valid) {
                log.warn("🚫 Webhook HMAC mismatch for Razorpay webhook payload.");
            }
            return valid;

        } catch (Exception e) {
            log.error("❌ HMAC-SHA256 computation failed", e);
            return false;
        }
    }

    // ── Payment Method ────────────────────────────────────────────────────────

    @Override
    public boolean savePaymentMethod(String workspaceId, String paymentMethodId) {
        log.info("💾 Razorpay: savePaymentMethod workspace={}, token={}", workspaceId, paymentMethodId);
        requireConfigured();
        return paymentMethodId != null && !paymentMethodId.isBlank();
    }

    @Override
    public String getProviderName() {
        return "RAZORPAY";
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Resolve the webhook secret from environment. Supports both dedicated webhook secret
     * and fallback to API key secret (not recommended for production).
     */
    private String resolveWebhookSecret() {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            return webhookSecret;
        }
        // Fallback to key secret if webhook secret not separately configured
        if (keySecret != null && !keySecret.isBlank()) {
            log.warn("⚠️ Using RAZORPAY_KEY_SECRET as webhook secret. Configure RAZORPAY_WEBHOOK_SECRET separately for production.");
            return keySecret;
        }
        return null;
    }

    private RazorpayClient client() throws RazorpayException {
        requireConfigured();
        return new RazorpayClient(keyId, keySecret);
    }

    private void requireConfigured() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException("Razorpay key id and key secret must be configured before payment actions.");
        }
    }

    private String normalizeCurrency(String override) {
        if (override != null && !override.isBlank()) {
            return override.toUpperCase();
        }
        return currency == null || currency.isBlank() ? "INR" : currency.toUpperCase();
    }

    private Map<String, Object> entityToMap(JSONObject json) {
        Map<String, Object> response = new HashMap<>();
        for (String key : json.keySet()) {
            response.put(key, json.get(key));
        }
        return response;
    }
}
