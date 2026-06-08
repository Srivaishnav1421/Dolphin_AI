package com.chubby.dolphin.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BrainAutomationPolicyServiceTest {

    private final BrainAutomationPolicyService policyService = new BrainAutomationPolicyService();

    @Test
    public void testEvaluatePolicyAutoApprove() {
        BrainAutomationPolicyService.AutomationDecision decision = policyService.evaluatePolicy(
                10.0, 90.0, 15.0, 95.0, false, false, false
        );
        assertEquals(BrainAutomationPolicyService.AutomationDecision.AUTO_APPROVE, decision);
    }

    @Test
    public void testEvaluatePolicyManualApproval() {
        BrainAutomationPolicyService.AutomationDecision decision = policyService.evaluatePolicy(
                30.0, 80.0, 5.0, 75.0, false, false, false
        );
        assertEquals(BrainAutomationPolicyService.AutomationDecision.MANUAL_APPROVAL, decision);
    }

    @Test
    public void testEvaluatePolicyBlock() {
        BrainAutomationPolicyService.AutomationDecision decision = policyService.evaluatePolicy(
                80.0, 50.0, 5.0, 30.0, true, true, true
        );
        assertEquals(BrainAutomationPolicyService.AutomationDecision.BLOCK, decision);
    }
}
