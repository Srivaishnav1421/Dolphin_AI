package com.chubby.dolphin.contentfactory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentFactoryItemRepository extends JpaRepository<ContentFactoryItem, UUID> {

    List<ContentFactoryItem> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<ContentFactoryItem> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
