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
            case HUGGINGFACE -> {
                double inputCost = (promptTokens * 3.50) / 1_000_000.0;
                double outputCost = (completionTokens * 10.50) / 1_000_000.0;
                yield inputCost + outputCost;
            }
        };
    }
}
