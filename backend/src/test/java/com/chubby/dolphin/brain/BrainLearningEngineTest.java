package com.chubby.dolphin.brain;

import com.chubby.dolphin.repository.BrainDecisionHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainLearningEngineTest {

    private StringRedisTemplate redisTemplate;
    private BrainDecisionHistoryRepository historyRepo;
    private ObjectMapper mapper;
    private BrainLearningEngine learningEngine;

    @BeforeEach
    public void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        historyRepo = mock(BrainDecisionHistoryRepository.class);
        mapper = new ObjectMapper();
        learningEngine = new BrainLearningEngine(redisTemplate, historyRepo, mapper);
    }

    @Test
    public void testGetLearningStatsFallback() {
        when(historyRepo.findByAccountId(anyString())).thenReturn(new ArrayList<>());
        when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));

        BrainLearningEngine.LearningStats stats = learningEngine.getLearningStats("w1", "SCALE_UP");
        assertNotNull(stats);
        assertEquals(0.84, stats.getSuccessRate());
        assertEquals(18.5, stats.getRoiRate());
    }

    @Test
    public void testUpdateStats() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        learningEngine.updateStats("w1", "SCALE_UP", 0.90, 0.10, 20.0, 15.0);
        verify(ops, times(1)).set(eq("brain:learning:w1:SCALE_UP"), anyString(), any());
    }
}
