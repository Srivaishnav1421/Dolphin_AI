package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.BrainFeedbackPattern;
import com.chubby.dolphin.repository.BrainFeedbackPatternRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BrainFeedbackServiceImpl implements BrainFeedbackService {

    private final BrainFeedbackPatternRepository feedbackRepo;

    public BrainFeedbackServiceImpl(BrainFeedbackPatternRepository feedbackRepo) {
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    @Transactional
    public void analyzeAndRecordFeedback(String campaignId, Double spend, Double revenue, 
                                          String tone, String audience, String platform, String product) {
        if (product == null || product.isBlank()) return;

        double roas = (spend != null && spend > 0) ? (revenue != null ? revenue / spend : 0.0) : 0.0;
        log.info("📊 Evaluating campaign metrics: Spend={}, Revenue={}, Calculated ROAS={}", spend, revenue, roas);

        String status = "NEUTRAL";
        if (roas >= 2.0) {
            status = "HIGH_PERFORMING";
        } else if (roas < 1.0 && spend != null && spend >= 10.0) {
            status = "LOW_PERFORMING";
        }

        if (!"NEUTRAL".equals(status)) {
            BrainFeedbackPattern pattern = new BrainFeedbackPattern();
            pattern.setProduct(product);
            pattern.setTone(tone);
            pattern.setAudience(audience);
            pattern.setPlatform(platform);
            pattern.setPatternStatus(status);
            pattern.setRoas(roas);
            pattern.setCreatedAt(LocalDateTime.now());

            feedbackRepo.save(pattern);
            log.info("💾 Recorded autonomous reinforcement feedback pattern [Status: {}, ROAS: {}]", status, roas);
        }
    }

    @Override
    public String getBrainOptimizationContext(String product) {
        if (product == null || product.isBlank()) {
            return "No historical performance context available.";
        }

        List<BrainFeedbackPattern> patterns = feedbackRepo.findByProductIgnoreCase(product.trim());
        if (patterns.isEmpty()) {
            return "No historical performance context is recorded for this product segment yet. Proceed with standard AI warm-start guidelines.";
        }

        List<BrainFeedbackPattern> high = patterns.stream()
                .filter(p -> "HIGH_PERFORMING".equals(p.getPatternStatus()))
                .collect(Collectors.toList());

        List<BrainFeedbackPattern> low = patterns.stream()
                .filter(p -> "LOW_PERFORMING".equals(p.getPatternStatus()))
                .collect(Collectors.toList());

        StringBuilder context = new StringBuilder("=== CRITICAL HISTORICAL PERFORMANCE LEARNINGS FOR THIS PRODUCT SEGMENT ===\n");
        context.append("Use the following historical data as reinforcement weights to improve click-through-rates (CTR) and ROAS:\n");

        if (!high.isEmpty()) {
            context.append("\n📈 HIGH-PERFORMING PARAMETERS (Leverage these tones and platforms):\n");
            for (BrainFeedbackPattern p : high) {
                context.append(String.format("- Tone: %s | Target Audience: %s | Platform: %s (Calculated ROAS: %.2f)\n", 
                        p.getTone(), p.getAudience(), p.getPlatform(), p.getRoas()));
            }
        }

        if (!low.isEmpty()) {
            context.append("\n📉 LOW-PERFORMING PARAMETERS (AVOID these completely):\n");
            for (BrainFeedbackPattern p : low) {
                context.append(String.format("- Tone: %s | Target Audience: %s | Platform: %s (Calculated ROAS: %.2f)\n", 
                        p.getTone(), p.getAudience(), p.getPlatform(), p.getRoas()));
            }
        }

        context.append("\nAdjust generated variations, headlines, and call-to-actions to lean heavily into the successful parameters and completely discard unsuccessful angles.");
        return context.toString();
    }
}
