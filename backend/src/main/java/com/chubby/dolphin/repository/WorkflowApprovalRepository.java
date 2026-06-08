package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WorkflowApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowApprovalRepository extends JpaRepository<WorkflowApproval, String> {

    List<WorkflowApproval> findByStatus(String status);

    List<WorkflowApproval> findByTraceId(String traceId);

    List<WorkflowApproval> findByWorkspaceId(String workspaceId);

    List<WorkflowApproval> findByWorkspaceIdAndStatus(String workspaceId, String status);

    java.util.Optional<WorkflowApproval> findByIdAndWorkspaceId(String id, String workspaceId);
}
