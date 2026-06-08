package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.MetricSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BrainScoringEngineTest {

    private final BrainScoringEngine engine = new BrainScoringEngine();

    @Test
    public void testConfidenceCalculationEmptyContext() {
        BrainContext context = BrainContext.builder()
                .campaigns(new ArrayList<>())
                .leads(new ArrayList<>())
                .metricSnapshots(new ArrayList<>())
                .recentDecisions(new ArrayList<>())
                .build();

        double score = engine.calculateConfidence(context);
        // Returns baseline historyScore (15) + default empty campaigns count (10) = 25
        assertTrue(score >= 20.0 && score <= 30.0);
    }

    @Test
    public void testConfidenceCalculationHealthyAccount() {
        Campaign c1 = new Campaign();
        c1.setDaysOfData(14);
        Campaign c2 = new Campaign();
        c2.setDaysOfData(10);

        List<Campaign> campaigns = new ArrayList<>();
        campaigns.add(c1);
        campaigns.add(c2);

        List<Lead> leads = Collections.nCopies(5, new Lead());
        List<MetricSnapshot> snapshots = Collections.nCopies(20, new MetricSnapshot());

        BrainDecision approvedDecision = new BrainDecision();
        approvedDecision.setStatus("APPROVED");

        BrainContext context = BrainContext.builder()
                .campaigns(campaigns)
                .leads(leads)
                .metricSnapshots(snapshots)
                .recentDecisions(Collections.singletonList(approvedDecision))
                .build();

        double score = engine.calculateConfidence(context);
        // Expected around 70 - 85
        assertTrue(score >= 50.0 && score <= 85.0);
    }
}
