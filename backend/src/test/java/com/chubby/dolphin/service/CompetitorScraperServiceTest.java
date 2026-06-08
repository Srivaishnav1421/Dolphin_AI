package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.CompetitorInsight;
import com.chubby.dolphin.repository.CompetitorInsightRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CompetitorScraperServiceTest {

    @Mock
    private CompetitorInsightRepository insightRepo;

    @Mock
    private BusinessLlmFacadeService llmRouter;

    private ObjectMapper mapper;
    private CompetitorScraperServiceImpl scraperService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        scraperService = new CompetitorScraperServiceImpl(insightRepo, llmRouter, mapper);
    }

    @Test
    public void testAnalyzeCompetitorSuccessfully() {
        String url = "https://example-competitor.com";
        String accountId = "test-account-123";
        
        String mockLlmJson = """
            {
              "value_proposition": "AI-driven marketing personalization",
              "hooks": [
                "Boost conversions with zero effort",
                "Self-correcting ad spends"
              ],
              "target_demographics": "Direct-to-consumer online brands",
              "pricing_model": "₹1499/month basic tier"
            }
            """;
            
        BusinessLlmFacadeService.LlmResponse mockResponse = new BusinessLlmFacadeService.LlmResponse(mockLlmJson, "GEMINI", "gemini-1.5-pro");
        when(llmRouter.ask(anyString())).thenReturn(mockResponse);
        
        when(insightRepo.save(any(CompetitorInsight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CompetitorInsight insight = scraperService.analyzeCompetitor(url, accountId);

        // Assert
        assertNotNull(insight);
        assertEquals(accountId, insight.getAccountId());
        assertEquals(url, insight.getCompetitorUrl());
        assertEquals("AI-driven marketing personalization", insight.getValueProposition());
        assertEquals("Direct-to-consumer online brands", insight.getTargetDemographics());
        assertEquals("₹1499/month basic tier", insight.getPricingModel());
        assertEquals(2, insight.getExtractedHooks().size());
        assertTrue(insight.getExtractedHooks().contains("Boost conversions with zero effort"));
        
        verify(insightRepo, times(1)).save(any(CompetitorInsight.class));
    }

    @Test
    public void testAnalyzeCompetitorLlmFallback() {
        String url = "https://broken-competitor.com";
        String accountId = "test-account-123";
        
        // Return broken/non-JSON text from LLM to trigger parsing fallback
        BusinessLlmFacadeService.LlmResponse mockResponse = new BusinessLlmFacadeService.LlmResponse("Internal error or plain sentence", "OLLAMA", "llama3");
        when(llmRouter.ask(anyString())).thenReturn(mockResponse);
        
        when(insightRepo.save(any(CompetitorInsight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CompetitorInsight insight = scraperService.analyzeCompetitor(url, accountId);

        // Assert
        assertNotNull(insight);
        assertEquals(accountId, insight.getAccountId());
        assertEquals(url, insight.getCompetitorUrl());
        // Should use fallback structures
        assertTrue(insight.getValueProposition().contains("Value proposition"));
        assertNotNull(insight.getExtractedHooks());
        assertFalse(insight.getExtractedHooks().isEmpty());
        
        verify(insightRepo, times(1)).save(any(CompetitorInsight.class));
    }
}
