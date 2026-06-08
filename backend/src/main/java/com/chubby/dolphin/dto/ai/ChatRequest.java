package com.chubby.dolphin.dto.ai;

/**
 * Standard chat request wrapping core prompts and customization telemetry variables.
 */
public class ChatRequest {
    private String systemPrompt;
    private String userPrompt;
    private Double temperature;
    private Integer maxTokens;

    public ChatRequest() {}

    public ChatRequest(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens) {
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
}
