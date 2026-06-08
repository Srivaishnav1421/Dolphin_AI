package com.chubby.dolphin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;
    private final String apiKey;

    public GeminiService(@Value("${gemini.api.url:}") String apiUrl,
            @Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl.isEmpty() ? "https://generativelanguage.googleapis.com" : apiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Send a prompt to Gemini Pro and get the text response.
     */
    public String ask(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "temperature", 0.3,
                            "maxOutputTokens", 1024));

            Map response = webClient.post()
                    .uri("?key=" + apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("candidates")) {
                List candidates = (List) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map candidate = (Map) candidates.get(0);
                    Map content = (Map) candidate.get("content");
                    List parts = (List) content.get("parts");
                    Map part = (Map) parts.get(0);
                    return (String) part.get("text");
                }
            }
        } catch (Exception e) {
            log.error("Gemini API error: {}", e.getMessage());
        }
        return "Analysis unavailable.";
    }

    /** Score a lead message — returns JSON string with score + status + signals */
    public String scoreLead(String leadMessage, String source) {
        String prompt = """
                You are an expert lead qualifier for a digital marketing agency.
                Analyze this incoming lead message and respond ONLY with a JSON object.

                Lead source: %s
                Lead message: "%s"

                Respond with ONLY this JSON (no explanation):
                {
                  "score": 0.85,
                  "status": "HOT",
                  "budget_signal": "High - mentioned ₹50k+ budget",
                  "timeline_signal": "Urgent - wants to start this week",
                  "intent_signal": "Strong - specific product inquiry",
                  "location_signal": "Chennai, Tamil Nadu",
                  "summary": "One-line qualification summary"
                }

                Rules:
                - score: 0.0-1.0 (HOT >= 0.7, WARM 0.4-0.69, COLD 0.2-0.39, UNQUALIFIABLE < 0.2)
                - status: HOT | WARM | COLD | UNQUALIFIABLE
                - Use "—" for signals not mentioned in the message
                """.formatted(source, leadMessage);
        return ask(prompt);
    }

    /** Evaluate a campaign and suggest action */
    public String evaluateCampaign(String name, double roas, double ctr, double cpl, double spent, double budget) {
        String prompt = """
                You are the Ad Brain of an autonomous marketing system.
                Evaluate this Meta ad campaign and recommend an action.

                Campaign: %s
                ROAS: %.2fx | CTR: %.2f%% | CPL: ₹%.0f | Spent: ₹%.0f / ₹%.0f budget

                Respond with ONLY this JSON:
                {
                  "action": "CONTINUE",
                  "confidence": 0.87,
                  "reason": "Strong ROAS above 3x threshold with healthy CTR",
                  "suggested_budget_change": 0
                }

                action options: CONTINUE | PAUSE | SCALE_UP | SCALE_DOWN
                suggested_budget_change: percentage (-50 to +100)
                """.formatted(name, roas, ctr, cpl, spent, budget);
        return ask(prompt);
    }

    /** Run budget arbitrage across campaigns */
    public String runArbitrage(List<String> campaignSummaries, double totalBudget) {
        String prompt = """
                You are managing ad budget arbitrage for a marketing agency.
                Total available budget: ₹%.0f

                Campaign performance:
                %s

                Recommend budget reallocation. Respond with ONLY this JSON:
                {
                  "recommendation": "Shift 20%% from Campaign B to Campaign A",
                  "expected_roas_lift": 0.4,
                  "actions": [
                    {"campaign": "Campaign A", "change": "+20%%"},
                    {"campaign": "Campaign B", "change": "-20%%"}
                  ],
                  "reasoning": "Campaign A has 3x better ROAS"
                }
                """.formatted(totalBudget, String.join("\n", campaignSummaries));
        return ask(prompt);
    }
}
