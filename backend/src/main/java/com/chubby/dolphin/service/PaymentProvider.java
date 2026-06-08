package com.chubby.dolphin.service;

import java.util.Map;

public interface PaymentProvider {
    Map<String, Object> createCustomer(String workspaceId, String email, String name);
    Map<String, Object> createSubscription(String workspaceId, String planId, int seats);
    boolean cancelSubscription(String workspaceId, String subscriptionId);
    Map<String, Object> createPayment(String workspaceId, double amount, String currency);
    boolean refundPayment(String workspaceId, String referenceId, double amount);
    boolean verifyWebhook(String signature, String payload);
    boolean savePaymentMethod(String workspaceId, String paymentMethodId);
    String getProviderName();
}
