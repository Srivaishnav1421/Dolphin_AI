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

public class AdvantageIntelligenceServiceTest {

    @Mock private BusinessLlmFacadeService llmRouter;
    @Mock private AdCreativeRepository creativeRepo;
    @Mock private ImageGenService imageGenService;

    private AdvantageIntelligenceService service;
    private ObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        service = new AdvantageIntelligenceService(llmRouter, creativeRepo, imageGenService, mapper);
    }

    @Test
    public void testGenerateMultiVariateGrid_ProducesCrossPermutations() {
        String workspaceId = "ws-123";
        String campaignId = "camp-777";
        String product = "Premium CRM Software";
        String audience = "Sales Executives";

        String jsonLlmResponse = """
            {
              "hooks": [
                "Hook 1",
                "Hook 2",
                "Hook 3"
              ],
              "bodies": [
                "Body 1",
                "Body 2",
                "Body 3"
              ],
              "ctas": ["LEARN_MORE", "SIGN_UP", "BOOK_NOW"]
            }
            """;

        BusinessLlmFacadeService.LlmResponse mockLlmResponse = new BusinessLlmFacadeService.LlmResponse(jsonLlmResponse, "GEMINI", "gemini-1.5-pro");
        when(llmRouter.ask(anyString())).thenReturn(mockLlmResponse);
        when(imageGenService.generateAdImage(anyString(), anyString())).thenReturn("http://image.url/test.png");
        when(creativeRepo.save(any(AdCreative.class))).thenAnswer(i -> i.getArgument(0));

        List<AdCreative> results = service.generateMultiVariateGrid(workspaceId, campaignId, product, audience);

        assertNotNull(results);
        assertEquals(9, results.size()); // 3 hooks * 3 bodies = 9 combinations
        
        AdCreative firstCreative = results.get(0);
        assertEquals("Hook 1", firstCreative.getHeadline());
        assertEquals("Body 1", firstCreative.getBody());
        assertEquals("LEARN_MORE", firstCreative.getCallToAction());
        assertEquals("MVT-H1-B1", firstCreative.getAbTestGroup());
        assertNotNull(firstCreative.getAbTestId());

        verify(creativeRepo, times(9)).save(any(AdCreative.class));
    }
}
