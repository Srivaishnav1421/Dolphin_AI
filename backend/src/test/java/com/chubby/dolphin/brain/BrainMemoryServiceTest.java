package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecisionHistory;
import com.chubby.dolphin.repository.BrainDecisionHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrainMemoryServiceTest {

    private BrainDecisionHistoryRepository historyRepo;
    private ObjectMapper mapper;
    private BrainMemoryService memoryService;

    @BeforeEach
    public void setUp() {
        historyRepo = mock(BrainDecisionHistoryRepository.class);
        mapper = new ObjectMapper();
        memoryService = new BrainMemoryService(historyRepo, mapper);
    }

    @Test
    public void testSaveMemorySummary() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("winRate", 75.0);

        memoryService.saveMemorySummary("w1", "experiment", data);

        ArgumentCaptor<BrainDecisionHistory> captor = ArgumentCaptor.forClass(BrainDecisionHistory.class);
        verify(historyRepo, times(1)).save(captor.capture());

        BrainDecisionHistory saved = captor.getValue();
        assertNotNull(saved);
        assertEquals("w1", saved.getAccountId());
        assertTrue(saved.getAction().startsWith("CONSOLIDATED_MEMORY_"));
        assertNotNull(saved.getMetricsAtDecision());
    }

    @Test
    public void testGetMemorySummaries() {
        List<BrainDecisionHistory> list = new ArrayList<>();
        BrainDecisionHistory h = new BrainDecisionHistory();
        h.setAction("CONSOLIDATED_MEMORY_TEST");
        h.setMetricsAtDecision("{\"type\":\"test\",\"rawSummaryJson\":\"{}\"}");
        list.add(h);

        when(historyRepo.findByAccountId("w1")).thenReturn(list);

        List<BrainMemoryService.BrainMemoryRecord> summaries = memoryService.getMemorySummaries("w1");
        assertNotNull(summaries);
        assertEquals(1, summaries.size());
        assertEquals("test", summaries.get(0).getType());
    }
}
