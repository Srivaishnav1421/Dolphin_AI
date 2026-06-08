package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.AdvantageExperiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdvantageExperimentRepository extends JpaRepository<AdvantageExperiment, String> {
    List<AdvantageExperiment> findByWorkspaceId(String workspaceId);
    List<AdvantageExperiment> findByStatus(String status);
    Optional<AdvantageExperiment> findByCampaignIdAndStatus(String campaignId, String status);
    Optional<AdvantageExperiment> findByIdAndWorkspaceId(String id, String workspaceId);
}
