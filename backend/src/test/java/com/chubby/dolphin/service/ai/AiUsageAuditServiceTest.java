package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmRequest;
import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.AiPurpose;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.repository.AiUsageLogRepository;
import com.chubby.dolphin.service.ai.audit.AiCostCalculator;
import com.chubby.dolphin.service.ai.audit.AiUsageAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AiUsageAuditServiceTest {

    @Mock
    private AiUsageLogRepository usageLogRepo;

    @Mock
    private AiCostCalculator costCalculator;

    private AiUsageAuditService auditService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        auditService = new AiUsageAuditService(usageLogRepo, costCalculator);
    }

    @Test
    public void testRecordUsageSavesLogEntryAndAppliesCosts() {
        LlmRequest request = LlmRequest.builder()
                .purpose(AiPurpose.SDR_CHAT)
                .workspaceId("ws-123")
                .prompt("Hello SDR")
                .build();

        LlmResponse response = LlmResponse.builder()
                .content("Reply from bot")
                .provider("HUGGINGFACE")
                .model("meta-llama/Llama-3.1-8B-Instruct")
                .promptTokens(1000)
                .completionTokens(2000)
                .totalTokens(3000)
                .build();

        // Configure cost calculation mock
        when(costCalculator.calculateCost(LlmProvider.HUGGINGFACE, 1000, 2000)).thenReturn(0.035); // $0.035 USD

        auditService.recordUsage("ws-123", request, response);

        // Verify the estimated cost was set in the response DTO
        assertEquals(0.035, response.getEstimatedCostUsd());

        // Verify that save was called on repository
        verify(usageLogRepo, times(1)).save(argThat(log -> 
                "ws-123".equals(log.getAccountId()) &&
                LlmProvider.HUGGINGFACE.equals(log.getProvider()) &&
                "meta-llama/Llama-3.1-8B-Instruct".equals(log.getModel()) &&
                Integer.valueOf(1000).equals(log.getPromptTokens()) &&
                Integer.valueOf(2000).equals(log.getCompletionTokens()) &&
                Integer.valueOf(3000).equals(log.getTotalTokens()) &&
                Double.valueOf(0.035).equals(log.getCostUsd()) &&
                AiPurpose.SDR_CHAT.equals(log.getPurpose())
        ));
    }
}
