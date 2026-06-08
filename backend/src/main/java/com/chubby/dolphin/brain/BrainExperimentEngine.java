package com.chubby.dolphin.brain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class BrainExperimentEngine {

    private final Random random = new Random();

    /**
     * UCB1 Algorithm: Multi-Armed Bandit Selector
     * Selects the next campaign action candidate, executing:
     * - Exploration (10%) to try new strategies
     * - Exploitation (90%) via UCB1 formula to pick historically rewarding arm
     */
    public String selectAction(String workspaceId, List<String> candidates, BrainLearningEngine learningEngine) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // 10% Exploration strategy
        if (random.nextDouble() < 0.10) {
            String explored = candidates.get(random.nextInt(candidates.size()));
            log.info("🎲 Exploration trigger activated (10% ratio) - Selected: {}", explored);
            return explored;
        }

        // 90% Exploitation strategy using UCB1 formula
        String bestArm = candidates.get(0);
        double maxUcb = -1.0;

        // Total trials (simulated or gathered from learning stats sum)
        double totalTrials = 100.0; 

        for (String candidate : candidates) {
            BrainLearningEngine.LearningStats stats = learningEngine.getLearningStats(workspaceId, candidate);
            double reward = stats.getSuccessRate();
            double n = Math.max(1.0, stats.getRoiRate()); // usage plays count representation

            double ucb = reward + Math.sqrt((2.0 * Math.log(totalTrials)) / n);
            if (ucb > maxUcb) {
                maxUcb = ucb;
                bestArm = candidate;
            }
        }

        log.info("🎯 Exploitation trigger activated (90% UCB1) - Selected Best UCB1 candidate: {} (value={})", bestArm, maxUcb);
        return bestArm;
    }
}
