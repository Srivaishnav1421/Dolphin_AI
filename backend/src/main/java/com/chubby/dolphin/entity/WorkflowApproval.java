package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_approvals")
@Data
@NoArgsConstructor
public class WorkflowApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String executionId;
    private String traceId;

    @Column(name = "workspace_id")
    private String workspaceId;

    private String status; // PENDING, APPROVED, REJECTED
    private String assignedTo;

    @Column(columnDefinition = "TEXT")
    private String decisionReason;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
