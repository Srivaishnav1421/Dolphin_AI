package com.chubby.dolphin.brain;

import com.chubby.dolphin.brain.execution.BrainOutcomeAnalyzer;
import com.chubby.dolphin.brain.execution.ExecutionScore;
import com.chubby.dolphin.entity.BrainDecision;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BrainOutcomeAnalyzerTest {

    private final BrainOutcomeAnalyzer analyzer = new BrainOutcomeAnalyzer();

    @Test
    public void testAnalyzeSuccessfulOutcome() {
        ExecutionScore score = analyzer.analyze(
                2.0, 3.0,  // ROAS before/after (+50%)
                2.0, 2.5,  // CTR before/after (+25%)
                15.0, 10.0, // CPC before/after (-33% cost reduction)
                0.0, 10.0  // Leads before/after
        );

        assertNotNull(score);
        assertTrue(score.getSuccessRate() > 0.0);
        assertTrue(score.getImpactScore() > 50.0);
        assertTrue(score.getRevenueImpact() > 0.0);
        assertEquals(5.0, score.getConfidenceAdjustment());
    }

    @Test
    public void testAnalyzeFailedOutcome() {
        ExecutionScore score = analyzer.analyze(
                2.5, 1.5,  // ROAS decreased
                3.0, 2.0,  // CTR decreased
                10.0, 15.0, // CPC increased
                0.0, 0.0
        );

        assertNotNull(score);
        assertEquals(0.0, score.getSuccessRate());
        assertTrue(score.getImpactScore() < 50.0);
        assertEquals(-10.0, score.getConfidenceAdjustment());
    }
}
