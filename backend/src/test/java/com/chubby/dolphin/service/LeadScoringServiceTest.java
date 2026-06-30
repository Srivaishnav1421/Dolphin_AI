package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.LeadInteraction;
import com.chubby.dolphin.repository.LeadInteractionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeadScoringServiceTest {

    private LeadInteractionRepository interactionRepository;
    private LeadScoringService service;

    @BeforeEach
    void setUp() {
        interactionRepository = mock(LeadInteractionRepository.class);
        service = new LeadScoringService(interactionRepository, new ObjectMapper());
    }

    @Test
    void formulaUsesOnlyRealAvailableFields() {
        Lead lead = lead();
        lead.setPhone("+919999999999");
        lead.setEmail("buyer@example.com");
        lead.setSource("WEBSITE");
        lead.setPipelineStage("INTERESTED");
        lead.setLocation("India");
        when(interactionRepository.findByLeadIdAndWorkspaceIdOrderByCreatedAtAsc(lead.getId(), lead.getWorkspaceId()))
                .thenReturn(List.of(recentInteraction()));

        var result = service.score(lead);

        assertEquals(100, result.score());
        assertEquals("HOT", result.temperature());
        assertEquals(20, result.breakdown().get("phone"));
        assertEquals(20, result.breakdown().get("email"));
        assertEquals(15, result.breakdown().get("sourceOrCampaign"));
        assertEquals(20, result.breakdown().get("interestedOrQualifiedStatus"));
        assertEquals(15, result.breakdown().get("recentActivityWithin2Days"));
        assertEquals(10, result.breakdown().get("locationRequirementOrNotes"));
    }

    @Test
    void missingPhoneAndEmailLowerScoreHonestly() {
        Lead lead = lead();
        lead.setSource("WEBSITE");
        lead.setMessage("Need marketing help");
        when(interactionRepository.findByLeadIdAndWorkspaceIdOrderByCreatedAtAsc(lead.getId(), lead.getWorkspaceId()))
                .thenReturn(List.of());

        var result = service.score(lead);

        assertEquals(25, result.score());
        assertEquals("COLD", result.temperature());
        assertEquals(0, result.breakdown().get("phone"));
        assertEquals(0, result.breakdown().get("email"));
    }

    @Test
    void temperatureThresholdsAreHotWarmColdUnknown() {
        assertEquals("UNKNOWN", service.score(lead()).temperature());

        Lead cold = lead();
        cold.setSource("REFERRAL");
        assertEquals("COLD", service.score(cold).temperature());

        Lead warm = lead();
        warm.setPhone("1");
        warm.setEmail("a@b.com");
        warm.setSource("FORM");
        assertEquals("WARM", service.score(warm).temperature());

        Lead hot = lead();
        hot.setPhone("1");
        hot.setEmail("a@b.com");
        hot.setSource("FORM");
        hot.setPipelineStage("INTERESTED");
        hot.setMessage("Need help");
        assertEquals("HOT", service.score(hot).temperature());
    }

    @Test
    void scoreClampsAtOneHundred() {
        Lead lead = lead();
        lead.setPhone("1");
        lead.setEmail("a@b.com");
        lead.setSource("FORM");
        lead.setPipelineStage("NEGOTIATION");
        lead.setMessage("Need help");
        lead.setLastContactedAt(LocalDateTime.now());

        assertEquals(100, service.score(lead).score());
    }

    @Test
    void recommendationUsesStatusAndActivityWithoutSendingAnything() {
        Lead hot = lead();
        hot.setPhone("1");
        hot.setEmail("a@b.com");
        hot.setSource("FORM");
        hot.setPipelineStage("INTERESTED");
        hot.setMessage("Need help");
        hot.setLastContactedAt(LocalDateTime.now());

        var hotAction = service.recommend(hot);
        assertEquals("Call within 30 minutes", hotAction.action());
        assertEquals("CALL_LEAD", hotAction.actionType());

        Lead won = lead();
        won.setPipelineStage("CONVERTED");
        var wonAction = service.recommend(won);
        assertEquals("No action required", wonAction.action());
    }

    private Lead lead() {
        Lead lead = new Lead();
        lead.setId("lead-1");
        lead.setWorkspaceId("workspace-1");
        lead.setName("Test Lead");
        return lead;
    }

    private LeadInteraction recentInteraction() {
        LeadInteraction interaction = new LeadInteraction();
        interaction.setCreatedAt(LocalDateTime.now().minusHours(1));
        return interaction;
    }
}
