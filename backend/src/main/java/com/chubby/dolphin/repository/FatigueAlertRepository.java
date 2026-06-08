package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.FatigueAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FatigueAlertRepository extends JpaRepository<FatigueAlert, String> {
    List<FatigueAlert> findByWorkspaceId(String workspaceId);
    List<FatigueAlert> findByCampaignId(String campaignId);
    List<FatigueAlert> findByCampaignIdAndStatus(String campaignId, String status);
}
