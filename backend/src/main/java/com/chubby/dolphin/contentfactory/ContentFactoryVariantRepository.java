package com.chubby.dolphin.contentfactory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentFactoryVariantRepository extends JpaRepository<ContentFactoryVariant, UUID> {

    List<ContentFactoryVariant> findByItemIdAndWorkspaceIdOrderByVariantIndexAsc(UUID itemId, UUID workspaceId);

    Optional<ContentFactoryVariant> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<ContentFactoryVariant> findByApprovalItemId(UUID approvalItemId);
}
