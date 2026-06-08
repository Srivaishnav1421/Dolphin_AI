package com.chubby.dolphin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AgentRuntimeService {

    private final BusinessLlmFacadeService llmRouter;

    public AgentRuntimeService(BusinessLlmFacadeService llmRouter) {
        this.llmRouter = llmRouter;
    }

    public Map<String, Object> executeAgent(String agentType, String message, Map<String, Object> metadata, String traceId) {
        log.info("🤖 Executing AI Agent Runtime [{}] — Message: '{}', Trace: {}", agentType, message, traceId);

        String prompt;
        String agentName;

        switch (agentType.toUpperCase()) {
            case "CHAT_AGENT":
                agentName = "Chat Agent";
                prompt = """
                        You are the Chat Agent of DolphinAI.
                        Your purpose is to answer customer questions, assist in onboarding, and provide helpful insights.
                        
                        Context: %s
                        User message: "%s"
                        
                        Please respond with a conversational but professional message.
                        """.formatted(metadata != null ? metadata.toString() : "No extra context", message);
                break;

            case "RESEARCH_AGENT":
                agentName = "Research Agent";
                prompt = """
                        You are the Research Agent of DolphinAI.
                        Your purpose is to analyze competitors, marketing campaign data, and target audience segments.
                        
                        Context: %s
                        Research Message/Query: "%s"
                        
                        Please perform a detailed breakdown and return clear, data-driven recommendations.
                        """.formatted(metadata != null ? metadata.toString() : "No extra context", message);
                break;

            case "DOCUMENT_AGENT":
                agentName = "Document Agent";
                prompt = """
                        You are the Document Agent of DolphinAI.
                        Your purpose is to generate campaign briefs, ad copy guidelines, and target persona descriptions.
                        
                        Context: %s
                        Document Prompt: "%s"
                        
                        Please draft a professional document with structured headings, descriptions, and copy suggestions.
                        """.formatted(metadata != null ? metadata.toString() : "No extra context", message);
                break;

            case "TASK_AUTOMATION_AGENT":
                agentName = "Task Automation Agent";
                prompt = """
                        You are the Task Automation Agent of DolphinAI.
                        Your purpose is to process rules and perform action recommendations (like optimizing budgets, pausing ads, or setting alert thresholds).
                        
                        Context: %s
                        Automation Query: "%s"
                        
                        Identify if any automated tasks are required and describe the execution plan.
                        """.formatted(metadata != null ? metadata.toString() : "No extra context", message);
                break;

            default:
                agentName = "General Agent";
                prompt = """
                        You are an AI Agent of DolphinAI.
                        User prompt: "%s"
                        """.formatted(message);
        }

        BusinessLlmFacadeService.LlmResponse response = llmRouter.ask(prompt);

        Map<String, Object> result = new HashMap<>();
        result.put("agentUsed", agentName);
        result.put("response", response.text());
        result.put("provider", response.provider());
        result.put("model", response.model());
        result.put("traceId", traceId);
        result.put("status", "SUCCESS");

        log.info("✅ Agent [{}] execution complete via LLM Router [{}]", agentName, response.provider());
        return result;
    }
}
