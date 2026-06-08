package com.chubby.dolphin.service.ai.audit;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.AiUsageLog;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.entity.AiPurpose;
import com.chubby.dolphin.repository.AiUsageLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUsageAuditService {

    private final AiUsageLogRepository usageLogRepo;
    private final AiCostCalculator costCalculator;

    public AiUsageAuditService(AiUsageLogRepository usageLogRepo, AiCostCalculator costCalculator) {
        this.usageLogRepo = usageLogRepo;
        this.costCalculator = costCalculator;
    }

    /**
     * Records token usage telemetry and calculates costs.
     */
    @Transactional
    public void recordUsage(String workspaceId, LlmRequest request, LlmResponse response) {
        if (request == null || response == null || workspaceId == null) {
            return;
        }

        LlmProvider providerEnum;
        try {
            providerEnum = LlmProvider.valueOf(response.getProvider().toUpperCase());
        } catch (Exception e) {
            providerEnum = LlmProvider.OLLAMA;
        }

        int promptTokens = response.getPromptTokens() != null ? response.getPromptTokens() : 0;
        int completionTokens = response.getCompletionTokens() != null ? response.getCompletionTokens() : 0;
        int totalTokens = response.getTotalTokens() != null ? response.getTotalTokens() : (promptTokens + completionTokens);

        // Compute cost and save in response DTO
        double costUsd = costCalculator.calculateCost(providerEnum, promptTokens, completionTokens);
        response.setEstimatedCostUsd(costUsd);

        // Make sure purpose maps cleanly
        AiPurpose purpose = request.getPurpose() != null ? request.getPurpose() : AiPurpose.GENERAL_ASSISTANT;

        AiUsageLog logEntry = AiUsageLog.builder()
                .accountId(workspaceId)
                .provider(providerEnum)
                .model(response.getModel() != null ? response.getModel() : "unknown")
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .costUsd(costUsd)
                .purpose(purpose)
                .build();

        usageLogRepo.save(logEntry);
    }
}
