package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.BrainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BrainEventRepository extends JpaRepository<BrainEvent, String> {
    List<BrainEvent> findTop50ByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    List<BrainEvent> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    List<BrainEvent> findTop50ByOrderByCreatedAtDesc();

    default List<BrainEvent> findTop50ByAccountIdOrderByCreatedAtDesc(String accountId) {
        return findTop50ByWorkspaceIdOrderByCreatedAtDesc(accountId);
    }

    default List<BrainEvent> findByAccountIdOrderByCreatedAtDesc(String accountId) {
        return findByWorkspaceIdOrderByCreatedAtDesc(accountId);
    }
}
