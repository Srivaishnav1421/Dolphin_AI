package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.BrainDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrainDecisionRepository extends JpaRepository<BrainDecision, String> {
    List<BrainDecision> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    List<BrainDecision> findTop50ByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    List<BrainDecision> findByStatus(String status);
    List<BrainDecision> findByCampaignIdOrderByCreatedAtDesc(String campaignId);
    List<BrainDecision> findByWorkspaceIdAndStatus(String workspaceId, String status);
    long countByWorkspaceIdAndStatus(String workspaceId, String status);
    java.util.Optional<BrainDecision> findByIdAndWorkspaceId(String id, String workspaceId);

    default List<BrainDecision> findByAccountIdOrderByCreatedAtDesc(String accountId) {
        return findByWorkspaceIdOrderByCreatedAtDesc(accountId);
    }
    default List<BrainDecision> findTop50ByAccountIdOrderByCreatedAtDesc(String accountId) {
        return findTop50ByWorkspaceIdOrderByCreatedAtDesc(accountId);
    }
    default List<BrainDecision> findByAccountIdAndStatus(String accountId, String status) {
        return findByWorkspaceIdAndStatus(accountId, status);
    }
    default long countByAccountIdAndStatus(String accountId, String status) {
        return countByWorkspaceIdAndStatus(accountId, status);
    }
}
