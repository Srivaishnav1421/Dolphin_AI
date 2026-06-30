package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.CompetitorAd;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.repository.CompetitorAdRepository;
import com.chubby.dolphin.repository.MetaConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CompetitorSpyServiceTest {

    @Mock private CompetitorAdRepository adRepo;
    @Mock private MetaConnectionRepository metaConnRepo;
    @Mock private BusinessLlmFacadeService llmRouter;

    private CompetitorSpyService service;
    private ObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        service = new CompetitorSpyService(adRepo, metaConnRepo, llmRouter, mapper);
    }

    @Test
    public void testAnalyzeAdText_ExtractsCorrectMetrics() {
        String adText = "Save 50% on our premium digital marketing services today only! Urgency!";

        String jsonResponse = """
            {
              "format": "IMAGE",
              "hook_type": "URGENCY",
              "offer_type": "DISCOUNT",
              "emotion": "URGENCY",
              "quality_score": 9
            }
            """;

        BusinessLlmFacadeService.LlmResponse mockLlmResponse = new BusinessLlmFacadeService.LlmResponse(jsonResponse, "GEMINI", "gemini-1.5-pro");
        when(llmRouter.ask(anyString())).thenReturn(mockLlmResponse);

        CompetitorAd result = service.analyzeAdText(adText);

        assertNotNull(result);
        assertEquals(adText, result.getAdText());
        assertEquals("IMAGE", result.getFormat());
        assertEquals("URGENCY", result.getHookType());
        assertEquals("DISCOUNT", result.getOfferType());
        assertEquals(9, result.getQualityScore());
    }

    @Test
    public void testSpyOnCompetitorWithoutMetaConnectionReturnsEmpty() {
        String workspaceId = "ws-111";
        String keyword = "Organic Coffee";

        when(metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID")).thenReturn(Optional.empty());

        List<CompetitorAd> results = service.spyOnCompetitor(workspaceId, keyword);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(adRepo, never()).save(any(CompetitorAd.class));
        verify(llmRouter, never()).ask(anyString());
    }
}
