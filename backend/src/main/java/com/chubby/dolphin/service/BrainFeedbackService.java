package com.chubby.dolphin.service;

public interface BrainFeedbackService {
    /**
     * Assesses historical campaign performance, determines high/low performing copy factors,
     * and saves reinforcement patterns.
     */
    void analyzeAndRecordFeedback(String campaignId, Double spend, Double revenue, 
                                  String tone, String audience, String platform, String product);

    /**
     * Compiles historical performance rules for a specific product context
     * to inject directly into LLM prompts.
     */
    String getBrainOptimizationContext(String product);
}
