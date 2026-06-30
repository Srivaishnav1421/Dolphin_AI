package com.chubby.dolphin.mathengine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignMathEvaluationRepository extends JpaRepository<CampaignMathEvaluation, UUID> {

    List<CampaignMathEvaluation> findTop20ByWorkspaceIdAndCampaignIdOrderByCreatedAtDesc(UUID workspaceId, UUID campaignId);

    List<CampaignMathEvaluation> findTop50ByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<CampaignMathEvaluation> findTop50ByWorkspaceIdAndRunIdOrderByCreatedAtDesc(UUID workspaceId, UUID runId);

    List<CampaignMathEvaluation> findTop20ByWorkspaceIdAndEvaluationTypeOrderByCreatedAtDesc(UUID workspaceId, String evaluationType);

    List<CampaignMathEvaluation> findTop10ByWorkspaceIdAndCampaignIdAndEvaluationTypeOrderByCreatedAtDesc(
            UUID workspaceId,
            UUID campaignId,
            String evaluationType
    );

    Optional<CampaignMathEvaluation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
