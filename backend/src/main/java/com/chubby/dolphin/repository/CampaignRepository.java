package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, String> {
    List<Campaign> findByWorkspaceId(String workspaceId);
    long countByWorkspaceIdIn(List<String> workspaceIds);
    List<Campaign> findByWorkspaceIdAndStatus(String workspaceId, String status);
    Optional<Campaign> findByIdAndWorkspaceId(String id, String workspaceId);
    List<Campaign> findByStatus(String status);

    default List<Campaign> findByAccountId(String accountId) {
        return findByWorkspaceId(accountId);
    }
    default List<Campaign> findByAccountIdAndStatus(String accountId, String status) {
        return findByWorkspaceIdAndStatus(accountId, status);
    }
}
