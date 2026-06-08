package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.LlmProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MockAiService implements AIService {

    @Value("${ai.mock.enabled:true}")
    private boolean enabled;

    @Override
    public LlmResponse ask(LlmRequest request) {
        return LlmResponse.builder()
                .content("[MOCK RESPONSE]")
                .provider("MOCK")
                .model(getModelName())
                .promptTokens(1)
                .completionTokens(1)
                .totalTokens(2)
                .estimatedCostUsd(0.0)
                .cached(false)
                .build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        return ask(request);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.MOCK;
    }

    @Override
    public String getModelName() {
        return "mock-llama3-ultra";
    }
}
