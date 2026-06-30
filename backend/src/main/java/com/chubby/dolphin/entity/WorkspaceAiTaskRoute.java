package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "workspace_ai_task_routes",
        uniqueConstraints = @UniqueConstraint(name = "uk_workspace_ai_task_route", columnNames = {"workspace_id", "task_key"}),
        indexes = @Index(name = "idx_workspace_ai_task_route_workspace", columnList = "workspace_id")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceAiTaskRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(name = "task_key", nullable = false, length = 80)
    private String taskKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private LlmProvider provider;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
