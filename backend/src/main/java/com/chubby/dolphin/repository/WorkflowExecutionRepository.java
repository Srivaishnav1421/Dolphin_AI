package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, String> {

    List<WorkflowExecution> findByWorkspaceId(String workspaceId);

    Optional<WorkflowExecution> findByExecutionId(String executionId);

    List<WorkflowExecution> findByTraceId(String traceId);

    Optional<WorkflowExecution> findByIdAndWorkspaceId(String id, String workspaceId);

    @Query("SELECT COUNT(w) FROM WorkflowExecution w WHERE w.workspaceId = :workspaceId AND w.status = :status")
    long countByWorkspaceIdAndStatus(@Param("workspaceId") String workspaceId, @Param("status") String status);

    @Query("SELECT AVG(w.executionDuration) FROM WorkflowExecution w WHERE w.workspaceId = :workspaceId AND w.executionDuration IS NOT NULL")
    Double getAverageDurationByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT w.agentUsed, COUNT(w) FROM WorkflowExecution w WHERE w.workspaceId = :workspaceId AND w.agentUsed IS NOT NULL GROUP BY w.agentUsed")
    List<Object[]> getAgentUsageStatsByWorkspaceId(@Param("workspaceId") String workspaceId);

    // Backward compatibility default methods:
    default List<WorkflowExecution> findByTenantId(String tenantId) {
        return findByWorkspaceId(tenantId);
    }
    default long countByTenantIdAndStatus(String tenantId, String status) {
        return countByWorkspaceIdAndStatus(tenantId, status);
    }
    default Double getAverageDurationByTenantId(String tenantId) {
        return getAverageDurationByWorkspaceId(tenantId);
    }
    default List<Object[]> getAgentUsageStatsByTenantId(String tenantId) {
        return getAgentUsageStatsByWorkspaceId(tenantId);
    }
}
