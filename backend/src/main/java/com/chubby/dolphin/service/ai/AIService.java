package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.LlmProvider;

/**
 * Standard AI provider abstraction layer for inference generation and operational metrics.
 */
public interface AIService {

    LlmResponse ask(LlmRequest request);

    LlmResponse chat(LlmRequest request);

    boolean isAvailable();

    boolean isEnabled();

    LlmProvider getProvider();

    String getModelName();
}
