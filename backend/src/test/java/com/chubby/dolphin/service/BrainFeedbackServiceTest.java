package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.BrainFeedbackPattern;
import com.chubby.dolphin.repository.BrainFeedbackPatternRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainFeedbackServiceTest {

    @Mock
    private BrainFeedbackPatternRepository feedbackRepo;

    private BrainFeedbackServiceImpl feedbackService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        feedbackService = new BrainFeedbackServiceImpl(feedbackRepo);
    }

    @Test
    public void testAnalyzeAndRecordFeedbackHighPerformance() {
        String campaignId = "camp-abc";
        Double spend = 100.0;
        Double revenue = 350.0; // ROAS = 3.5
        String tone = "casual";
        String audience = "young-adults";
        String platform = "FACEBOOK_FEED";
        String product = "Premium Coffee Blend";

        when(feedbackRepo.save(any(BrainFeedbackPattern.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        feedbackService.analyzeAndRecordFeedback(campaignId, spend, revenue, tone, audience, platform, product);

        // Assert
        verify(feedbackRepo, times(1)).save(argThat(pattern -> 
            "HIGH_PERFORMING".equals(pattern.getPatternStatus()) &&
            pattern.getRoas() == 3.5 &&
            "casual".equals(pattern.getTone()) &&
            "Premium Coffee Blend".equals(pattern.getProduct())
        ));
    }

    @Test
    public void testAnalyzeAndRecordFeedbackLowPerformance() {
        String campaignId = "camp-xyz";
        Double spend = 50.0;
        Double revenue = 10.0; // ROAS = 0.2
        String tone = "luxury";
        String audience = "students";
        String platform = "REELS";
        String product = "Affordable Notebooks";

        when(feedbackRepo.save(any(BrainFeedbackPattern.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        feedbackService.analyzeAndRecordFeedback(campaignId, spend, revenue, tone, audience, platform, product);

        // Assert
        verify(feedbackRepo, times(1)).save(argThat(pattern -> 
            "LOW_PERFORMING".equals(pattern.getPatternStatus()) &&
            pattern.getRoas() == 0.2 &&
            "luxury".equals(pattern.getTone()) &&
            "Affordable Notebooks".equals(pattern.getProduct())
        ));
    }

    @Test
    public void testGetBrainOptimizationContextEmpty() {
        String product = "Unknown Gadget";
        when(feedbackRepo.findByProductIgnoreCase(anyString())).thenReturn(new ArrayList<>());

        // Act
        String context = feedbackService.getBrainOptimizationContext(product);

        // Assert
        assertTrue(context.contains("No historical performance context"));
    }

    @Test
    public void testGetBrainOptimizationContextWithData() {
        String product = "Ergonomic Chair";
        
        List<BrainFeedbackPattern> patterns = new ArrayList<>();
        
        BrainFeedbackPattern p1 = new BrainFeedbackPattern();
        p1.setProduct(product);
        p1.setTone("professional");
        p1.setAudience("remote-workers");
        p1.setPlatform("FACEBOOK_FEED");
        p1.setPatternStatus("HIGH_PERFORMING");
        p1.setRoas(3.1);
        patterns.add(p1);

        BrainFeedbackPattern p2 = new BrainFeedbackPattern();
        p2.setProduct(product);
        p2.setTone("urgent");
        p2.setAudience("remote-workers");
        p2.setPlatform("REELS");
        p2.setPatternStatus("LOW_PERFORMING");
        p2.setRoas(0.4);
        patterns.add(p2);

        when(feedbackRepo.findByProductIgnoreCase(product)).thenReturn(patterns);

        // Act
        String context = feedbackService.getBrainOptimizationContext(product);

        // Assert
        assertNotNull(context);
        assertTrue(context.contains("HIGH-PERFORMING PARAMETERS"));
        assertTrue(context.contains("professional"));
        assertTrue(context.contains("LOW-PERFORMING PARAMETERS"));
        assertTrue(context.contains("urgent"));
    }
}
