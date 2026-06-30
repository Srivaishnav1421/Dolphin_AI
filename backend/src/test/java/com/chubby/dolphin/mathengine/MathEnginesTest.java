package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.entity.MetricSnapshot;
import com.chubby.dolphin.entity.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MathEnginesTest {

    private CampaignPerformanceScoreEngine performanceEngine;
    private WalletSafetyEngine walletEngine;
    private CplThresholdEngine cplEngine;
    private FortyEightHourKillRuleEngine killRuleEngine;
    private CreativeFatigueEngine fatigueEngine;
    private GrowthOpportunityEngine opportunityEngine;
    private RiskEngine riskEngine;

    @BeforeEach
    void setUp() {
        performanceEngine = new CampaignPerformanceScoreEngine();
        walletEngine = new WalletSafetyEngine();
        cplEngine = new CplThresholdEngine();
        killRuleEngine = new FortyEightHourKillRuleEngine();
        fatigueEngine = new CreativeFatigueEngine();
        opportunityEngine = new GrowthOpportunityEngine();
        riskEngine = new RiskEngine();

        ReflectionTestUtils.setField(performanceEngine, "defaultTargetCpl", 500.0);
        ReflectionTestUtils.setField(walletEngine, "lowThreshold", 1000.0);
        ReflectionTestUtils.setField(walletEngine, "criticalThreshold", 0.0);
        ReflectionTestUtils.setField(walletEngine, "dailySpendCap", 5000.0);
        ReflectionTestUtils.setField(cplEngine, "defaultTargetCpl", 500.0);
        ReflectionTestUtils.setField(cplEngine, "noLeadSpendThreshold", 500.0);
        ReflectionTestUtils.setField(killRuleEngine, "minimumSpend", 500.0);
        ReflectionTestUtils.setField(killRuleEngine, "hours", 48L);
        ReflectionTestUtils.setField(fatigueEngine, "lowCtrPercent", 0.5);
        ReflectionTestUtils.setField(fatigueEngine, "highCpc", 125.0);
        ReflectionTestUtils.setField(fatigueEngine, "clicksNoConversionThreshold", 100L);
        ReflectionTestUtils.setField(opportunityEngine, "defaultTargetCpl", 500.0);
        ReflectionTestUtils.setField(opportunityEngine, "conversionRateThreshold", 5.0);
        ReflectionTestUtils.setField(opportunityEngine, "minimumSpend", 500.0);
        ReflectionTestUtils.setField(riskEngine, "defaultTargetCpl", 500.0);
        ReflectionTestUtils.setField(riskEngine, "lowWalletThreshold", 1000.0);
        ReflectionTestUtils.setField(riskEngine, "noRecentLeadsDays", 7L);
    }

    @Test
    void performanceScoreNormalCaseCalculatesDeterministically() {
        Campaign c = campaign();
        c.setCtr(1.0);
        c.setCpl(250.0);
        c.setSpent(1000.0);
        c.setConversions(5);

        MathSignal signal = performanceEngine.evaluate(c, null, 500.0);

        assertEquals(MathEvaluationStatus.OK, signal.status());
        assertEquals(100.0, signal.score());
        assertEquals(MathSeverity.LOW, signal.severity());
        assertEquals(MathActionType.NONE, signal.actionType());
        assertEquals(50.0, ((java.util.Map<?, ?>) signal.inputSnapshot().get("scoreBreakdown")).get("base"));
        assertEquals(false, signal.inputSnapshot().get("notEnoughData"));
    }

    @Test
    void performanceScoreHandlesZeroSpendAndZeroConversions() {
        Campaign c = campaign();
        c.setCtr(1.0);
        c.setSpent(0.0);
        c.setConversions(0);

        MathSignal signal = performanceEngine.evaluate(c, null, 500.0);

        assertEquals(MathEvaluationStatus.OK, signal.status());
        assertTrue(signal.score() >= 0);
        assertFalse(signal.requiresApproval());
    }

    @Test
    void performanceScoreDerivesCtrFromClicksAndImpressions() {
        MetricSnapshot s = snapshot();
        s.setClicks(10L);
        s.setImpressions(1000L);
        s.setSpend(200.0);
        s.setConversions(1L);

        MathSignal signal = performanceEngine.evaluate(campaign(), s, 500.0);

        assertEquals(MathEvaluationStatus.OK, signal.status());
        assertTrue(signal.inputSnapshot().toString().contains("ctrPercent"));
    }

    @Test
    void performanceScoreExplainsMissingCtrAndUsesDefaultTargetCpl() {
        Campaign c = campaign();
        c.setCpl(250.0);
        c.setSpent(100.0);
        c.setConversions(1);

        MathSignal signal = performanceEngine.evaluate(c, null, null);

        assertEquals(MathEvaluationStatus.OK, signal.status());
        assertTrue(signal.inputSnapshot().toString().contains("CTR missing"));
        assertTrue(signal.inputSnapshot().toString().contains("using configured default target CPL"));
        assertEquals(0.0, ((java.util.Map<?, ?>) signal.inputSnapshot().get("scoreBreakdown")).get("ctrScore"));
    }

    @Test
    void performanceScoreDerivesCplFromSpendAndLeadsWithoutDividingByZero() {
        Campaign c = campaign();
        c.setSpent(1000.0);
        c.setConversions(2);
        MetricSnapshot snapshot = snapshot();
        snapshot.setSpend(1000.0);
        snapshot.setLeads(4L);

        MathSignal signal = performanceEngine.evaluate(c, snapshot, 500.0);

        assertEquals(MathEvaluationStatus.OK, signal.status());
        assertTrue(signal.inputSnapshot().toString().contains("actualCpl=250.0"));

        MetricSnapshot zeroLead = snapshot();
        zeroLead.setSpend(1000.0);
        zeroLead.setLeads(0L);
        MathSignal zeroLeadSignal = performanceEngine.evaluate(campaign(), zeroLead, 500.0);
        assertEquals(MathEvaluationStatus.OK, zeroLeadSignal.status());
        assertEquals(0.0, ((java.util.Map<?, ?>) zeroLeadSignal.inputSnapshot().get("scoreBreakdown")).get("cplScore"));
    }

    @Test
    void performanceScoreNoDataReturnsNotEnoughData() {
        MathSignal signal = performanceEngine.evaluate(campaign(), null, 500.0);

        assertEquals(MathEvaluationStatus.NOT_ENOUGH_DATA, signal.status());
        assertNull(signal.score());
        assertEquals(true, signal.inputSnapshot().get("notEnoughData"));
    }

    @Test
    void performanceScoreClampsToHundredAndAppliesPenalty() {
        Campaign high = campaign();
        high.setCtr(10.0);
        high.setCpl(1.0);
        high.setConversions(100);
        high.setSpent(1000.0);
        assertEquals(100.0, performanceEngine.evaluate(high, null, 500.0).score());

        Campaign penalized = campaign();
        penalized.setCtr(0.1);
        penalized.setSpent(600.0);
        penalized.setConversions(0);
        MathSignal signal = performanceEngine.evaluate(penalized, null, 500.0);
        assertTrue(signal.inputSnapshot().toString().contains("penalty=30.0"));
        assertTrue(signal.score() >= 0);
        assertEquals(0.0, MathEngineUtils.clamp(-10.0, 0.0, 100.0));
    }

    @Test
    void walletSafetySignalsCriticalLowNormalAndCapBreach() {
        Wallet depleted = wallet(0.0);
        assertEquals(MathActionType.PAUSE_ALL_REQUIRED, walletEngine.evaluate(depleted, 0.0).actionType());
        assertEquals(MathSeverity.CRITICAL, walletEngine.evaluate(depleted, 0.0).severity());

        assertEquals(MathActionType.ALERT_LOW_WALLET, walletEngine.evaluate(wallet(500.0), 0.0).actionType());
        assertEquals(MathActionType.NONE, walletEngine.evaluate(wallet(5000.0), 1000.0).actionType());

        MathSignal cap = walletEngine.evaluate(wallet(10000.0), 6000.0);
        assertEquals(MathActionType.CHANGE_BUDGET, cap.actionType());
        assertTrue(cap.requiresApproval());
    }

    @Test
    void cplThresholdTracksConsecutiveBreaches() {
        Campaign c = campaign();
        c.setCpl(800.0);
        c.setObjective("LEADS");

        MathSignal first = cplEngine.evaluate(c, null, 500.0, List.of());
        assertEquals(MathActionType.MONITOR, first.actionType());
        assertFalse(first.requiresApproval());

        CampaignMathEvaluation prev1 = previousCpl(MathActionType.MONITOR);
        MathSignal second = cplEngine.evaluate(c, null, 500.0, List.of(prev1));
        assertEquals(MathActionType.MONITOR, second.actionType());

        CampaignMathEvaluation prev2 = previousCpl(MathActionType.MONITOR);
        MathSignal third = cplEngine.evaluate(c, null, 500.0, List.of(prev2, prev1));
        assertEquals(MathActionType.CHANGE_OBJECTIVE, third.actionType());
        assertTrue(third.requiresApproval());
    }

    @Test
    void cplThresholdClassifiesNoLeadSpendAsWatchingBeforeThreeCycles() {
        Campaign c = campaign();
        c.setSpent(700.0);
        c.setConversions(0);

        MathSignal first = cplEngine.evaluate(c, null, null, List.of());
        assertEquals(MathActionType.MONITOR, first.actionType());
        assertFalse(first.requiresApproval());
        assertTrue(first.description().contains("high CPL/no-lead risk"));

        MathSignal third = cplEngine.evaluate(c, null, null,
                List.of(previousCpl(MathActionType.MONITOR), previousCpl(MathActionType.MONITOR)));
        assertEquals(MathActionType.CHANGE_OBJECTIVE, third.actionType());
        assertTrue(third.requiresApproval());
    }

    @Test
    void cplThresholdMissingAndNonConsecutiveCases() {
        assertEquals(MathEvaluationStatus.NOT_ENOUGH_DATA,
                cplEngine.evaluate(campaign(), null, 500.0, List.of()).status());

        Campaign c = campaign();
        c.setCpl(800.0);
        CampaignMathEvaluation reset = previousCpl(MathActionType.NONE);
        assertEquals(MathActionType.MONITOR, cplEngine.evaluate(c, null, 500.0, List.of(reset)).actionType());
    }

    @Test
    void killRuleOnlySignalsWhenAllConditionsMatch() {
        Campaign old = campaign();
        old.setCreatedAt(LocalDateTime.now().minusHours(50));
        old.setSpent(700.0);
        old.setConversions(0);
        MathSignal kill = killRuleEngine.evaluate(old, LocalDateTime.now());
        assertEquals(MathActionType.KILL_CAMPAIGN, kill.actionType());
        assertEquals(MathSeverity.CRITICAL, kill.severity());
        assertTrue(kill.requiresApproval());

        Campaign young = campaign();
        young.setCreatedAt(LocalDateTime.now().minusHours(10));
        young.setSpent(700.0);
        young.setConversions(0);
        assertEquals(MathActionType.MONITOR, killRuleEngine.evaluate(young, LocalDateTime.now()).actionType());

        Campaign missing = campaign();
        missing.setCreatedAt(null);
        assertEquals(MathEvaluationStatus.NOT_ENOUGH_DATA, killRuleEngine.evaluate(missing, LocalDateTime.now()).status());
    }

    @Test
    void creativeFatigueSignalsLowCtrHighCpcAndNoConversionClicks() {
        Campaign lowCtr = campaign();
        lowCtr.setCtr(0.3);
        assertEquals(MathActionType.REVIEW_CREATIVE, fatigueEngine.evaluate(lowCtr, null).actionType());

        MetricSnapshot highCpc = snapshot();
        highCpc.setSpend(2000.0);
        highCpc.setClicks(10L);
        assertEquals(MathActionType.REDUCE_BID, fatigueEngine.evaluate(campaign(), highCpc).actionType());

        MetricSnapshot noConversions = snapshot();
        noConversions.setClicks(101L);
        noConversions.setConversions(0L);
        assertEquals(MathActionType.REVIEW_LANDING_PAGE, fatigueEngine.evaluate(campaign(), noConversions).actionType());

        assertEquals(MathEvaluationStatus.NOT_ENOUGH_DATA, fatigueEngine.evaluate(campaign(), null).status());
    }

    @Test
    void growthOpportunitiesOnlyComeFromRealSignals() {
        Campaign c = campaign();
        c.setCpl(300.0);
        c.setRoas(3.0);
        c.setConversionRate(6.0);
        c.setSpent(1000.0);
        c.setConversions(3);

        List<MathSignal> opportunities = opportunityEngine.evaluate(c, 500.0, 80.0);

        assertTrue(opportunities.stream().anyMatch(s -> s.title().contains("Low CPL")));
        assertTrue(opportunities.stream().anyMatch(s -> s.title().contains("ROAS")));
        assertTrue(opportunities.stream().anyMatch(s -> s.actionType() == MathActionType.INCREASE_BUDGET && s.requiresApproval()));
        assertTrue(opportunityEngine.evaluate(campaign(), 500.0, null).isEmpty());
    }

    @Test
    void riskEngineOnlyGeneratesProvenRisks() {
        Campaign c = campaign();
        c.setCpl(900.0);
        c.setSpent(700.0);
        c.setConversions(0);

        Lead oldLead = new Lead();
        oldLead.setCreatedAt(LocalDateTime.now().minusDays(20));

        List<MathSignal> risks = riskEngine.evaluate(c, wallet(500.0), List.of(oldLead), 500.0, 0.2, 200.0);

        assertTrue(risks.stream().anyMatch(s -> s.title().contains("High CPL")));
        assertTrue(risks.stream().anyMatch(s -> s.title().contains("zero conversions") || s.title().contains("Zero")));
        assertTrue(risks.stream().anyMatch(s -> s.title().contains("No recent leads")));
        assertTrue(riskEngine.evaluate(campaign(), null, null, 500.0, null, null).isEmpty());
    }

    private Campaign campaign() {
        Campaign c = new Campaign();
        c.setId("11111111-1111-1111-1111-111111111111");
        c.setWorkspaceId("22222222-2222-2222-2222-222222222222");
        c.setName("Test Campaign");
        c.setStatus("ACTIVE");
        c.setObjective("LEADS");
        c.setConversions(null);
        c.setConversionRate(null);
        return c;
    }

    private MetricSnapshot snapshot() {
        MetricSnapshot s = new MetricSnapshot();
        s.setCampaignId("11111111-1111-1111-1111-111111111111");
        s.setAccountId("22222222-2222-2222-2222-222222222222");
        return s;
    }

    private Wallet wallet(double balance) {
        Wallet wallet = new Wallet();
        wallet.setId("33333333-3333-3333-3333-333333333333");
        wallet.setAccountId("22222222-2222-2222-2222-222222222222");
        wallet.setBalance(balance);
        wallet.setDailyBudgetLimit(10000.0);
        return wallet;
    }

    private CampaignMathEvaluation previousCpl(MathActionType actionType) {
        CampaignMathEvaluation evaluation = new CampaignMathEvaluation();
        evaluation.setEvaluationType(CplThresholdEngine.EVALUATION_TYPE);
        evaluation.setActionType(actionType);
        evaluation.setStatus(MathEvaluationStatus.OK);
        return evaluation;
    }
}
