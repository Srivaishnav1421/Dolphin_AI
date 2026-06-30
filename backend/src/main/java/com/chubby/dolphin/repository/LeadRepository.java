package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, String> {
    List<Lead> findByWorkspaceId(String workspaceId);
    long countByWorkspaceIdIn(List<String> workspaceIds);
    List<Lead> findByWorkspaceIdAndStatus(String workspaceId, String status);
    List<Lead> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    List<Lead> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(String workspaceId, String status);
    java.util.Optional<Lead> findByIdAndWorkspaceId(String id, String workspaceId);

    default List<Lead> findByAccountId(String accountId) {
        return findByWorkspaceIdOrderByCreatedAtDesc(accountId);
    }
    default List<Lead> findByAccountIdAndStatus(String accountId, String status) {
        return findByWorkspaceIdAndStatusOrderByCreatedAtDesc(accountId, status);
    }
    
    java.util.Optional<Lead> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    @org.springframework.data.jpa.repository.Query("SELECT l FROM Lead l WHERE l.createdAt < :targetTime " +
           "AND l.lastReply IS NULL AND l.optedOut = false AND l.status IS NOT NULL AND l.status <> 'COLD'")
    List<Lead> findLeadsForFollowUp(@org.springframework.data.repository.query.Param("targetTime") java.time.LocalDateTime targetTime);
}
