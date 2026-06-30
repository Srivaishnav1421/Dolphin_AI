package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.repository.LeadInteractionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LeadScoringService {

    public static final String FORMULA_VERSION = "lead-score-v1";

    private final LeadInteractionRepository interactionRepository;
    private final ObjectMapper objectMapper;

    public LeadScoringService(LeadInteractionRepository interactionRepository, ObjectMapper objectMapper) {
        this.interactionRepository = interactionRepository;
        this.objectMapper = objectMapper;
    }

    public LeadScoreResult score(Lead lead) {
        int phone = present(lead.getPhone()) ? 20 : 0;
        int email = present(lead.getEmail()) ? 20 : 0;
        int source = present(lead.getSource()) || present(lead.getCampaignId()) ? 15 : 0;
        int status = highIntentStatus(lead.getStatus(), lead.getPipelineStage()) ? 20 : 0;
        int recentActivity = hasRecentActivity(lead) ? 15 : 0;
        int qualificationData = present(lead.getLocation())
                || present(lead.getMessage())
                || present(lead.getNotes())
                || present(lead.getInterestCategory()) ? 10 : 0;

        int total = clamp(phone + email + source + status + recentActivity + qualificationData);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("formulaVersion", FORMULA_VERSION);
        breakdown.put("phone", phone);
        breakdown.put("email", email);
        breakdown.put("sourceOrCampaign", source);
        breakdown.put("interestedOrQualifiedStatus", status);
        breakdown.put("recentActivityWithin2Days", recentActivity);
        breakdown.put("locationRequirementOrNotes", qualificationData);
        breakdown.put("score", total);
        breakdown.put("temperature", temperature(total));
        breakdown.put("reason", reason(total, breakdown));
        return new LeadScoreResult(total, temperature(total), breakdown, toJson(breakdown));
    }

    public NextActionResult recommend(Lead lead) {
        LeadScoreResult score = score(lead);
        String status = normalize(lead.getStatus());
        String stage = normalize(lead.getPipelineStage());
        String actionType = "FOLLOW_UP";
        String action;

        if ("WON".equals(status) || "CONVERTED".equals(stage)) {
            action = "No action required";
            actionType = "FOLLOW_UP";
        } else if ("LOST".equals(status) || "UNQUALIFIABLE".equals(status) || "LOST".equals(stage)) {
            action = "No action required";
            actionType = "FOLLOW_UP";
        } else if (score.score() >= 75) {
            action = "Call within 30 minutes";
            actionType = "CALL_LEAD";
        } else if (score.score() >= 45) {
            action = "Send follow-up message";
            actionType = "SEND_WHATSAPP";
        } else if (hasNoActivity(lead)) {
            action = "Contact this lead today";
            actionType = "FOLLOW_UP";
        } else if ("CONTACTED".equals(stage) && staleForTwoDays(lead)) {
            action = "Follow up";
            actionType = "FOLLOW_UP";
        } else if (score.score() > 0) {
            action = "Ask one qualifying question";
            actionType = "FOLLOW_UP";
        } else {
            action = "Collect contact details before prioritizing";
            actionType = "FOLLOW_UP";
        }
        return new NextActionResult(action, actionType, score);
    }

    public void applyScoreToNewLead(Lead lead) {
        LeadScoreResult result = score(lead);
        lead.setScore(result.score() / 100.0);
        lead.setConversionProbability(result.score() / 100.0);
        lead.setPriority(priority(result.score()));
        lead.setNextBestAction(recommend(lead).action());
        lead.setTemperature(result.temperature());
        lead.setPipelineStage(stage(result.temperature()));
        lead.setGeminiAnalysis(result.breakdownJson());
    }

    public void refreshScoreWithoutStatusOverride(Lead lead) {
        LeadScoreResult result = score(lead);
        lead.setScore(result.score() / 100.0);
        lead.setConversionProbability(result.score() / 100.0);
        lead.setPriority(priority(result.score()));
        lead.setNextBestAction(recommend(lead).action());
        lead.setTemperature(result.temperature());
        lead.setGeminiAnalysis(result.breakdownJson());
    }

    private boolean hasRecentActivity(Lead lead) {
        if (lead.getLastContactedAt() != null && lead.getLastContactedAt().isAfter(LocalDateTime.now().minusDays(2))) {
            return true;
        }
        if (lead.getLastReplyAt() != null && lead.getLastReplyAt().isAfter(LocalDateTime.now().minusDays(2))) {
            return true;
        }
        if (lead.getId() == null) {
            return false;
        }
        return interactionRepository.findByLeadIdAndWorkspaceIdOrderByCreatedAtAsc(lead.getId(), lead.getWorkspaceId()).stream()
                .anyMatch(row -> row.getCreatedAt() != null && row.getCreatedAt().isAfter(LocalDateTime.now().minusDays(2)));
    }

    private boolean hasNoActivity(Lead lead) {
        return lead.getLastContactedAt() == null
                && lead.getLastReplyAt() == null
                && (lead.getId() == null || interactionRepository.findByLeadIdAndWorkspaceIdOrderByCreatedAtAsc(lead.getId(), lead.getWorkspaceId()).isEmpty());
    }

    private boolean staleForTwoDays(Lead lead) {
        return lead.getLastContactedAt() == null || lead.getLastContactedAt().isBefore(LocalDateTime.now().minusDays(2));
    }

    private boolean highIntentStatus(String status, String stage) {
        String normalizedStatus = normalize(status);
        String normalizedStage = normalize(stage);
        return "INTERESTED".equals(normalizedStatus)
                || "QUALIFIED".equals(normalizedStatus)
                || "HOT".equals(normalizedStatus)
                || "INTERESTED".equals(normalizedStage)
                || "QUALIFIED".equals(normalizedStage)
                || "PROPOSAL_SENT".equals(normalizedStage)
                || "NEGOTIATION".equals(normalizedStage);
    }

    private String temperature(int score) {
        if (score >= 75) return "HOT";
        if (score >= 45) return "WARM";
        if (score > 0) return "COLD";
        return "UNKNOWN";
    }

    private String priority(int score) {
        if (score >= 85) return "URGENT";
        if (score >= 75) return "HIGH";
        if (score >= 45) return "MEDIUM";
        return "LOW";
    }

    private String stage(String temperature) {
        return switch (temperature) {
            case "HOT" -> "INTERESTED";
            case "WARM" -> "QUALIFIED";
            default -> "NEW_LEAD";
        };
    }

    private String reason(int score, Map<String, Object> breakdown) {
        if (score == 0) return "No scoring signals available yet.";
        return "Lead Score is based on available contact, source, status, activity, and requirement data.";
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase().replace(' ', '_');
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public record LeadScoreResult(int score, String temperature, Map<String, Object> breakdown, String breakdownJson) {}
    public record NextActionResult(String action, String actionType, LeadScoreResult score) {}
}
