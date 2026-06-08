package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.AdCreative;
import com.chubby.dolphin.repository.AdCreativeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CreativeAIServiceTest {

    @Mock
    private BusinessLlmFacadeService llmRouter;

    @Mock
    private AdCreativeRepository creativeRepo;

    @Mock
    private ImageGenService imageGenService;

    private ObjectMapper mapper;
    private CreativeAIService creativeService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        creativeService = new CreativeAIService(llmRouter, creativeRepo, mapper, imageGenService);
    }

    @Test
    public void testGenerateAdCopySuccessfullyWithVisuals() {
        String accountId = "acc-999";
        String campaignId = "camp-111";
        String product = "Smart Watch X";
        String audience = "Tech Enthusiasts";
        String tone = "Exciting";
        String platform = "FACEBOOK_FEED";

        String mockLlmJson = """
            {
              "variations": [
                {
                  "headline": "Own the Future on Your Wrist",
                  "body": "Get the Smart Watch X with active visual tracker.",
                  "cta": "SHOP_NOW",
                  "predicted_ctr": 4.85
                }
              ]
            }
            """;

        BusinessLlmFacadeService.LlmResponse mockLlmResponse = new BusinessLlmFacadeService.LlmResponse(mockLlmJson, "GEMINI", "gemini-1.5-pro");
        when(llmRouter.generateAdCopy(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockLlmResponse);

        String mockImgUrl = "https://images.unsplash.com/mock-smart-watch-url";
        when(imageGenService.generateAdImage(anyString(), eq(product))).thenReturn(mockImgUrl);

        when(creativeRepo.save(any(AdCreative.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<AdCreative> results = creativeService.generateAdCopy(accountId, campaignId, product, audience, tone, platform);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        AdCreative creative = results.get(0);
        assertEquals("Own the Future on Your Wrist", creative.getHeadline());
        assertEquals("SHOP_NOW", creative.getCallToAction());
        assertEquals(mockImgUrl, creative.getImageUrl());
        assertEquals("AI_GENERATED", creative.getGeneratedBy());

        verify(creativeRepo, times(1)).save(any(AdCreative.class));
        verify(imageGenService, times(1)).generateAdImage(anyString(), eq(product));
    }
}
