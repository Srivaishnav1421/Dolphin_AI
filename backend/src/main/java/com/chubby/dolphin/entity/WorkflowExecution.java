package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_executions")
@Data
@NoArgsConstructor
public class WorkflowExecution {

    @Id
    private String id; // UUID generated on backend before dispatching

    private String workflowId;
    private String workflowName;
    private String workflowVersion;

    @Column(columnDefinition = "TEXT")
    private String workflowSnapshot;

    private String executionId;
    private String traceId;
    private String parentTraceId;

    @Column(name = "tenant_id")
    private String workspaceId;
    private String projectId;
    private String userId;

    public String getTenantId() {
        return workspaceId;
    }

    public void setTenantId(String tenantId) {
        this.workspaceId = tenantId;
    }

    private String agentUsed;
    private String status; // RUNNING, WAITING_FOR_APPROVAL, COMPLETED, FAILED

    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;
    private Long executionDuration; // In milliseconds

    @Column(columnDefinition = "TEXT")
    private String userRequest;

    @Column(columnDefinition = "TEXT")
    private String finalResponse;

    @Column(columnDefinition = "TEXT")
    private String errorLogs;
}
