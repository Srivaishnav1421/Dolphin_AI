package com.chubby.dolphin.service.ai.audit;

import com.chubby.dolphin.entity.LlmProvider;
import org.springframework.stereotype.Service;

@Service
public class AiCostCalculator {

    /**
     * Calculates the estimated cost in USD based on provider pricing charts.
     */
    public double calculateCost(LlmProvider provider, int promptTokens, int completionTokens) {
        if (provider == null) {
            return 0.0;
        }
        return switch (provider) {
            case OLLAMA, MOCK -> 0.0;
            case OPENAI -> {
                double inputCost = (promptTokens * 0.40) / 1_000_000.0;
                double outputCost = (completionTokens * 1.60) / 1_000_000.0;
                yield inputCost + outputCost;
            }
            case GEMINI -> {
                double inputCost = (promptTokens * 0.075) / 1_000_000.0;
                double outputCost = (completionTokens * 0.30) / 1_000_000.0;
                yield inputCost + outputCost;
            }
            case ANTHROPIC -> {
                double inputCost = (promptTokens * 3.00) / 1_000_000.0;
                double outputCost = (completionTokens * 15.00) / 1_000_000.0;
                yield inputCost + outputCost;
            }
            case HUGGINGFACE -> {
                double inputCost = (promptTokens * 3.50) / 1_000_000.0;
                double outputCost = (completionTokens * 10.50) / 1_000_000.0;
                yield inputCost + outputCost;
            }
        };
    }
}
