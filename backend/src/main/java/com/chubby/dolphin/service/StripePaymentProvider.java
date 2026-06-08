package com.chubby.dolphin.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service("stripePaymentProvider")
public class StripePaymentProvider implements PaymentProvider {

    @Override
    public Map<String, Object> createCustomer(String workspaceId, String email, String name) {
        throw new UnsupportedOperationException("Stripe is not supported in the India-First phase.");
    }

    @Override
    public Map<String, Object> createSubscription(String workspaceId, String planId, int seats) {
        throw new UnsupportedOperationException("Stripe is not supported in the India-First phase.");
    }

    @Override
    public boolean cancelSubscription(String workspaceId, String subscriptionId) {
        throw new UnsupportedOperationException("Stripe is not supported in the India-First phase.");
    }

    @Override
    public Map<String, Object> createPayment(String workspaceId, double amount, String currency) {
        throw new UnsupportedOperationException("Stripe is not supported in the India-First phase.");
    }

    @Override
    public boolean refundPayment(String workspaceId, String referenceId, double amount) {
        throw new UnsupportedOperationException("Stripe is not supported in the India-First phase.");
    }

    @Override
    public boolean verifyWebhook(String signature, String payload) {
        throw new UnsupportedOperationException("Stripe is not supported in the India-First phase.");
    }

    @Override
    public boolean savePaymentMethod(String workspaceId, String paymentMethodId) {
        throw new UnsupportedOperationException("Stripe is not supported in the India-First phase.");
    }

    @Override
    public String getProviderName() {
        return "STRIPE";
    }
}
