package com.chubby.dolphin.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalItemRepository extends JpaRepository<ApprovalItem, UUID> {

    List<ApprovalItem> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<ApprovalItem> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, ApprovalStatus status);

    long countByWorkspaceIdAndStatus(UUID workspaceId, ApprovalStatus status);

    Optional<ApprovalItem> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<ApprovalItem> findByWorkspaceIdAndStatusAndExpiresAtBefore(
            UUID workspaceId,
            ApprovalStatus status,
            LocalDateTime expiresAt
    );

    boolean existsByWorkspaceIdAndSourceModuleAndSourceEntityIdAndActionTypeAndStatus(
            UUID workspaceId,
            ApprovalSourceModule sourceModule,
            UUID sourceEntityId,
            ApprovalActionType actionType,
            ApprovalStatus status
    );
}
