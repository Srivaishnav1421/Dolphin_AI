package com.chubby.dolphin.adbrain;

import com.chubby.dolphin.adbrain.dto.AdBrainRunResultDto;
import com.chubby.dolphin.adbrain.dto.AdBrainSignalDto;
import com.chubby.dolphin.mathengine.dto.CampaignMathEvaluationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdBrainService {

    private final AdBrainRunService runService;

    public AdBrainRunResultDto runCurrentWorkspace() {
        return runService.runCurrentWorkspace();
    }

    public AdBrainRunResultDto latestStatus() {
        return runService.latestStatus();
    }

    public List<AdBrainRunResultDto> recentRuns() {
        return runService.recentRuns();
    }

    public AdBrainRunResultDto runById(UUID id) {
        return runService.runById(id);
    }

    public List<AdBrainSignalDto> latestSignals() {
        return runService.latestSignals();
    }

    public List<CampaignMathEvaluationResponse> latestEvaluations() {
        return runService.latestEvaluations();
    }

    public List<CampaignMathEvaluationResponse> evaluationsForRun(UUID runId) {
        return runService.evaluationsForRun(runId);
    }

    public CampaignMathEvaluationResponse evaluationById(UUID id) {
        return runService.evaluationById(id);
    }
}
