package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.Lead;
import com.chubby.dolphin.growth.dto.ChurnPrediction;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChurnPredictionEngine {

    private final CampaignRepository campaignRepo;
    private final LeadRepository leadRepo;

    public ChurnPrediction predictChurn(String workspaceId) {
        log.info("🔮 Running predictive churn auditing for workspace: {}", workspaceId);

        List<Campaign> campaigns = campaignRepo.findByAccountId(workspaceId);
        List<Lead> leads = leadRepo.findByAccountId(workspaceId);

        double probability = 10.0; 
        List<String> riskFactors = new ArrayList<>();
        List<String> interventions = new ArrayList<>();

        long activeCount = campaigns.stream().filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus())).count();
        if (activeCount == 0) {
            probability += 35.0;
            riskFactors.add("Zero active marketing campaigns detected in portfolio.");
            interventions.add("Re-activate high-ROAS creative variants immediately.");
        }

        double totalBudget = campaigns.stream().mapToDouble(c -> c.getBudget() != null ? c.getBudget() : 0.0).sum();
        if (totalBudget < 500.0 && activeCount > 0) {
            probability += 15.0;
            riskFactors.add("Budget allocation is severely underfunded (below ₹500/day).");
            interventions.add("Perform dynamic budget scale-up of 25% on top performing arms.");
        }

        if (leads.isEmpty()) {
            probability += 25.0;
            riskFactors.add("Lead generation pipeline is dry (zero leads recorded).");
            interventions.add("Relaunch Meta Advantage+ Lead Permutations Grid to drive capture.");
        } else {
            long coldLeads = leads.stream().filter(l -> "COLD".equalsIgnoreCase(l.getStatus())).count();
            double coldRatio = (double) coldLeads / leads.size();
            if (coldRatio > 0.6) {
                probability += 15.0;
                riskFactors.add("High ratio of cold/unresponsive audience segments (" + Math.round(coldRatio * 100) + "%).");
                interventions.add("Deploy retargeting with conversational WhatsApp CTA elements.");
            }
        }

        probability = Math.clamp(probability, 5.0, 99.0);

        if (interventions.isEmpty()) {
            interventions.add("Maintain automated closed-loop learning parameters.");
        }

        return ChurnPrediction.builder()
                .churnProbability(probability)
                .riskFactors(riskFactors)
                .recommendedInterventions(interventions)
                .build();
    }
}
