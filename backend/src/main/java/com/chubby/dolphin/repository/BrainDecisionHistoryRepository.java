package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.BrainDecisionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrainDecisionHistoryRepository extends JpaRepository<BrainDecisionHistory, String> {
    List<BrainDecisionHistory> findByWorkspaceId(String workspaceId);
    List<BrainDecisionHistory> findByCampaignId(String campaignId);
    List<BrainDecisionHistory> findByDecisionId(String decisionId);
    List<BrainDecisionHistory> findTop50ByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    java.util.Optional<BrainDecisionHistory> findByIdAndWorkspaceId(String id, String workspaceId);

    default List<BrainDecisionHistory> findByAccountId(String accountId) {
        return findByWorkspaceId(accountId);
    }
    default List<BrainDecisionHistory> findTop50ByAccountIdOrderByCreatedAtDesc(String accountId) {
        return findTop50ByWorkspaceIdOrderByCreatedAtDesc(accountId);
    }
}
