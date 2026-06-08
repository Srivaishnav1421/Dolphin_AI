package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Wallet;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class BrainRiskEvaluatorTest {

    private final BrainRiskEvaluator evaluator = new BrainRiskEvaluator();

    @Test
    public void testLowRiskScenario() {
        Wallet w = new Wallet();
        w.setBalance(8000.0);

        BrainContext context = BrainContext.builder()
                .wallet(w)
                .campaigns(Collections.emptyList())
                .leads(Collections.emptyList())
                .build();

        BrainDecision recommendation = new BrainDecision();
        recommendation.setDecisionType("RESUME");

        double risk = evaluator.calculateRisk(context, recommendation);
        // Low risk: should be below 30
        assertTrue(risk <= 30.0);
    }

    @Test
    public void testHighRiskScenarioBankruptWallet() {
        Wallet w = new Wallet();
        w.setBalance(0.0); // bankrupt

        BrainContext context = BrainContext.builder()
                .wallet(w)
                .campaigns(Collections.emptyList())
                .leads(Collections.emptyList())
                .build();

        BrainDecision recommendation = new BrainDecision();
        recommendation.setDecisionType("SCALE_UP");
        recommendation.setBudgetBefore(100.0);
        recommendation.setBudgetAfter(200.0); // 100% budget increase

        double risk = evaluator.calculateRisk(context, recommendation);
        // High risk expected due to zero wallet balance and massive budget jump
        assertTrue(risk >= 50.0);
    }
}
