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
    public void testSpyOnCompetitorFallback_GeneratesMockAds() {
        String workspaceId = "ws-111";
        String keyword = "Organic Coffee";

        String simulatedJson = """
            {
              "ads": [
                {
                  "brand_name": "Eco Brew",
                  "ad_text": "Taste the pure organic coffee direct from hills of Coorg.",
                  "format": "VIDEO",
                  "hook_type": "STATEMENT",
                  "offer_type": "DISCOUNT",
                  "emotion": "JOY",
                  "quality_score": 8
                }
              ]
            }
            """;

        BusinessLlmFacadeService.LlmResponse mockLlmResponse = new BusinessLlmFacadeService.LlmResponse(simulatedJson, "GEMINI", "gemini-1.5-pro");
        when(llmRouter.ask(anyString())).thenReturn(mockLlmResponse);

        when(metaConnRepo.findFirstByAccountIdAndTokenStatus(workspaceId, "VALID")).thenReturn(Optional.empty());
        when(adRepo.save(any(CompetitorAd.class))).thenAnswer(i -> i.getArgument(0));

        List<CompetitorAd> results = service.spyOnCompetitor(workspaceId, keyword);

        assertNotNull(results);
        assertEquals(1, results.size());
        CompetitorAd ad = results.get(0);
        assertEquals("Eco Brew", ad.getPageName());
        assertEquals("Taste the pure organic coffee direct from hills of Coorg.", ad.getAdText());
        assertEquals("VIDEO", ad.getFormat());
        assertEquals(8, ad.getQualityScore());
        verify(adRepo, times(1)).save(any(CompetitorAd.class));
    }
}
