package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecisionHistory;
import com.chubby.dolphin.repository.BrainDecisionHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class BrainLearningEngine {

    private final StringRedisTemplate redisTemplate;
    private final BrainDecisionHistoryRepository historyRepo;
    private final ObjectMapper mapper;

    @Autowired
    public BrainLearningEngine(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            BrainDecisionHistoryRepository historyRepo,
            ObjectMapper mapper) {
        this.redisTemplate = redisTemplate;
        this.historyRepo = historyRepo;
        this.mapper = mapper;
    }

    public LearningStats getLearningStats(String workspaceId, String decisionType) {
        String key = "brain:learning:" + workspaceId + ":" + decisionType;
        try {
            if (redisTemplate != null) {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return mapper.readValue(cached, LearningStats.class);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis cache read failure in learning engine: {}", e.getMessage());
        }

        // Fallback: Calculate from database
        LearningStats dbStats = calculateFromDb(workspaceId, decisionType);
        
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, mapper.writeValueAsString(dbStats), Duration.ofHours(24));
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis cache write failure in learning engine: {}", e.getMessage());
        }

        return dbStats;
    }

    public void updateStats(String workspaceId, String decisionType, double successRate, double failureRate, double roiRate, double riskRate) {
        LearningStats stats = new LearningStats(successRate, failureRate, roiRate, riskRate);
        String key = "brain:learning:" + workspaceId + ":" + decisionType;
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, mapper.writeValueAsString(stats), Duration.ofHours(24));
                log.info("🧠 Learning statistics updated in Redis for key: {}", key);
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis cache update failure: {}", e.getMessage());
        }
    }

    private LearningStats calculateFromDb(String workspaceId, String decisionType) {
        List<BrainDecisionHistory> history = historyRepo.findByAccountId(workspaceId);
        if (history == null || history.isEmpty()) {
            return getDefaultBaseline(decisionType);
        }

        long total = history.stream()
                .filter(h -> h.getAction() != null && h.getAction().toUpperCase().contains(decisionType.toUpperCase()))
                .count();

        if (total == 0) {
            return getDefaultBaseline(decisionType);
        }

        long successful = history.stream()
                .filter(h -> h.getAction() != null && h.getAction().toUpperCase().contains(decisionType.toUpperCase()))
                .filter(h -> "EXECUTED".equals(h.getStatus()) || "APPROVED".equals(h.getStatus()))
                .count();

        double successRate = (double) successful / total;
        double failureRate = 1.0 - successRate;
        double roi = "SCALE_UP".equalsIgnoreCase(decisionType) ? 18.4 : 8.2;
        double risk = "SCALE_UP".equalsIgnoreCase(decisionType) ? 15.0 : 5.0;

        return new LearningStats(successRate, failureRate, roi, risk);
    }

    private LearningStats getDefaultBaseline(String decisionType) {
        if ("SCALE_UP".equalsIgnoreCase(decisionType)) {
            return new LearningStats(0.84, 0.16, 18.5, 18.0);
        } else if ("PAUSE".equalsIgnoreCase(decisionType)) {
            return new LearningStats(0.78, 0.22, 10.0, 5.0);
        } else if ("CHANGE_CREATIVE".equalsIgnoreCase(decisionType)) {
            return new LearningStats(0.71, 0.29, 12.0, 8.0);
        } else if ("CHANGE_AUDIENCE".equalsIgnoreCase(decisionType)) {
            return new LearningStats(0.67, 0.33, 14.0, 12.0);
        }
        return new LearningStats(0.75, 0.25, 12.0, 10.0);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningStats {
        private double successRate;
        private double failureRate;
        private double roiRate;
        private double riskRate;
    }
}
