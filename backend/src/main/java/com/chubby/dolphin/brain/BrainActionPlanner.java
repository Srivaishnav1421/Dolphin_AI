package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecision;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BrainActionPlanner {

    public List<String> buildPlan(BrainDecision recommendation) {
        List<String> plan = new ArrayList<>();
        if (recommendation == null) {
            plan.add("Verify workspace configuration parameters");
            plan.add("Broadcast execution status");
            return plan;
        }

        String type = recommendation.getDecisionType() != null ? recommendation.getDecisionType() : "CONTINUE";

        switch (type) {
            case "PAUSE":
                plan.add("Verify active campaign status in database");
                plan.add("Validate Meta connection authority");
                plan.add("Pause campaign execution via Meta Ads API");
                plan.add("Notify account administrators via WhatsApp sequence");
                plan.add("Broadcast execution status");
                break;

            case "SCALE_UP":
                plan.add("Verify wallet balance sufficiency");
                plan.add("Validate Meta connection authority");
                if (recommendation.getBudgetBefore() != null && recommendation.getBudgetAfter() != null) {
                    double before = recommendation.getBudgetBefore();
                    double after = recommendation.getBudgetAfter();
                    double pct = ((after - before) / before) * 100.0;
                    plan.add(String.format("Increase campaign budget by %.0f%% (₹%.0f → ₹%.0f)", pct, before, after));
                } else {
                    plan.add("Increase campaign budget by 20%");
                }
                plan.add("Monitor CTR and ROAS indicators for 24 hours");
                plan.add("Broadcast execution status");
                break;

            case "SCALE_DOWN":
                plan.add("Verify wallet balance sufficiency");
                plan.add("Validate Meta connection authority");
                if (recommendation.getBudgetBefore() != null && recommendation.getBudgetAfter() != null) {
                    double before = recommendation.getBudgetBefore();
                    double after = recommendation.getBudgetAfter();
                    double pct = ((before - after) / before) * 100.0;
                    plan.add(String.format("Decrease campaign budget by %.0f%% (₹%.0f → ₹%.0f)", pct, before, after));
                } else {
                    plan.add("Decrease campaign budget by 20%");
                }
                plan.add("Monitor CTR and ROAS indicators for 24 hours");
                plan.add("Broadcast execution status");
                break;

            case "RESUME":
                plan.add("Verify wallet balance sufficiency");
                plan.add("Validate Meta connection authority");
                plan.add("Resume campaign execution via Meta Ads API");
                plan.add("Monitor lead flow generation rate");
                plan.add("Broadcast execution status");
                break;

            case "BUDGET_REALLOCATE":
                plan.add("Verify wallet balance sufficiency");
                plan.add("Validate Meta connection authority");
                plan.add("Perform dynamic budget reallocation across ad sets");
                plan.add("Monitor overall conversion velocity");
                plan.add("Broadcast execution status");
                break;

            case "CREATE_CAMPAIGN":
                plan.add("Verify wallet balance sufficiency");
                plan.add("Load creative copy and ad structures");
                plan.add("Deploy new campaign structure to Meta Ads Manager");
                plan.add("Monitor initial conversion rates");
                plan.add("Broadcast execution status");
                break;

            case "CHANGE_CREATIVE":
                plan.add("Fetch high-performing creative from Ad Brain");
                plan.add("Rotate fatigued creatives in active Ad Set");
                plan.add("Verify creative rendering and destination link");
                plan.add("Monitor engagement scores");
                plan.add("Broadcast execution status");
                break;

            default:
                plan.add("Verify wallet balance");
                plan.add("Validate Meta connection");
                plan.add("Increase campaign budget by 20%");
                plan.add("Monitor CTR for 24 hours");
                plan.add("Broadcast execution status");
                break;
        }

        return plan;
    }
}
