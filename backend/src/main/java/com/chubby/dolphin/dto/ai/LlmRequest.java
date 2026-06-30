package com.chubby.dolphin.dto.ai;

import com.chubby.dolphin.entity.AiPurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {
    private String prompt;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private AiPurpose purpose;
    private String taskKey;
    private String workspaceId;
}
