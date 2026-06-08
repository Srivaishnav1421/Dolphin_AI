package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.AiPurpose;
import com.chubby.dolphin.entity.AiUsageLog;
import com.chubby.dolphin.entity.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, String> {

    long countByProvider(LlmProvider provider);

    List<AiUsageLog> findByProvider(LlmProvider provider);

    List<AiUsageLog> findByPurpose(AiPurpose purpose);

    @Query("SELECT COALESCE(SUM(l.costUsd), 0.0) FROM AiUsageLog l WHERE l.accountId = :workspaceId")
    double sumCostByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT COALESCE(SUM(l.costUsd), 0.0) FROM AiUsageLog l WHERE l.accountId = :workspaceId AND l.createdAt >= :since")
    double sumCostByWorkspaceIdSince(@Param("workspaceId") String workspaceId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(l.totalTokens), 0L) FROM AiUsageLog l WHERE l.accountId = :workspaceId AND l.createdAt >= :since")
    long sumTokensByWorkspaceIdSince(@Param("workspaceId") String workspaceId, @Param("since") LocalDateTime since);
}
