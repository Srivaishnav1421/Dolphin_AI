package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.AdCreative;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdCreativeRepository extends JpaRepository<AdCreative, String> {
    List<AdCreative> findByWorkspaceId(String workspaceId);
    List<AdCreative> findByCampaignId(String campaignId);
    List<AdCreative> findByWorkspaceIdAndStatus(String workspaceId, String status);
    List<AdCreative> findByWorkspaceIdAndStatusIn(String workspaceId, java.util.Collection<String> statuses);
    java.util.Optional<AdCreative> findByIdAndWorkspaceId(String id, String workspaceId);

    default List<AdCreative> findByAccountId(String accountId) {
        return findByWorkspaceId(accountId);
    }
    default List<AdCreative> findByAccountIdAndStatus(String accountId, String status) {
        return findByWorkspaceIdAndStatus(accountId, status);
    }
    default List<AdCreative> findByAccountIdAndStatusIn(String accountId, java.util.Collection<String> statuses) {
        return findByWorkspaceIdAndStatusIn(accountId, statuses);
    }
    List<AdCreative> findByAbTestId(String abTestId);
    List<AdCreative> findByCampaignIdAndStatus(String campaignId, String status);
}
