package com.chubby.dolphin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service("razorpayPaymentProvider")
public class RazorpayPaymentProvider implements PaymentProvider {

    @Value("${razorpay.webhook.secret:dummy_secret}")
    private String webhookSecret;

    @Override
    public Map<String, Object> createCustomer(String workspaceId, String email, String name) {
        Map<String, Object> response = new HashMap<>();
        response.put("customer_id", "cust_rzp_" + UUID.randomUUID().toString().replace("-", ""));
        response.put("email", email);
        response.put("name", name);
        return response;
    }

    @Override
    public Map<String, Object> createSubscription(String workspaceId, String planId, int seats) {
        Map<String, Object> response = new HashMap<>();
        response.put("subscription_id", "sub_rzp_" + UUID.randomUUID().toString().replace("-", ""));
        response.put("provider", "razorpay");
        response.put("status", "created");
        return response;
    }

    @Override
    public boolean cancelSubscription(String workspaceId, String subscriptionId) {
        // Cancel logic with Razorpay API mock
        return true;
    }

    @Override
    public Map<String, Object> createPayment(String workspaceId, double amount, String currency) {
        Map<String, Object> response = new HashMap<>();
        response.put("order_id", "order_rzp_" + UUID.randomUUID().toString().replace("-", ""));
        response.put("amount", amount);
        response.put("currency", "INR");
        return response;
    }

    @Override
    public boolean refundPayment(String workspaceId, String referenceId, double amount) {
        return true;
    }

    @Override
    public boolean verifyWebhook(String signature, String payload) {
        if (signature == null || payload == null) {
            return false;
        }
        try {
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);
            byte[] hash = sha256HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean savePaymentMethod(String workspaceId, String paymentMethodId) {
        return true;
    }

    @Override
    public String getProviderName() {
        return "RAZORPAY";
    }
}
