package com.chubby.dolphin.brain;

import com.chubby.dolphin.entity.BrainDecisionHistory;
import com.chubby.dolphin.repository.BrainDecisionHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class BrainMemoryService {

    private final BrainDecisionHistoryRepository historyRepo;
    private final ObjectMapper mapper;

    public BrainMemoryService(BrainDecisionHistoryRepository historyRepo, ObjectMapper mapper) {
        this.historyRepo = historyRepo;
        this.mapper = mapper;
    }

    public void saveMemorySummary(String workspaceId, String summaryType, Object summaryData) {
        try {
            BrainMemoryRecord record = new BrainMemoryRecord(summaryType, mapper.writeValueAsString(summaryData));
            String serialized = mapper.writeValueAsString(record);

            BrainDecisionHistory history = new BrainDecisionHistory();
            history.setAccountId(workspaceId);
            history.setDecisionId("MEM-" + System.currentTimeMillis());
            history.setCampaignId("MEM-CAM");
            history.setAction("CONSOLIDATED_MEMORY_" + summaryType.toUpperCase());
            history.setMetricsAtDecision(serialized); // Store inside metrics field!
            history.setStatus("LOGGED_ONLY");
            history.setConfidenceScore(100.0);
            history.setRiskScore(0.0);

            historyRepo.save(history);
            log.info("💾 Brain memory segment consolidated and saved successfully: type={}", summaryType);
        } catch (Exception e) {
            log.error("Failed to serialize and save brain memory summary: {}", e.getMessage());
        }
    }

    public List<BrainMemoryRecord> getMemorySummaries(String workspaceId) {
        List<BrainMemoryRecord> list = new ArrayList<>();
        try {
            List<BrainDecisionHistory> histories = historyRepo.findByAccountId(workspaceId);
            if (histories != null) {
                for (BrainDecisionHistory h : histories) {
                    if (h.getAction() != null && h.getAction().startsWith("CONSOLIDATED_MEMORY_") && h.getMetricsAtDecision() != null) {
                        BrainMemoryRecord rec = mapper.readValue(h.getMetricsAtDecision(), BrainMemoryRecord.class);
                        list.add(rec);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse and retrieve memory summaries: {}", e.getMessage());
        }
        return list;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrainMemoryRecord {
        private String type;
        private String rawSummaryJson;
    }
}
