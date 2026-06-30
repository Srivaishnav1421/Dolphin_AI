package com.chubby.dolphin.adbrain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdBrainRunRepository extends JpaRepository<AdBrainRunSummary, UUID> {

    Optional<AdBrainRunSummary> findTopByWorkspaceIdOrderByStartedAtDesc(UUID workspaceId);

    List<AdBrainRunSummary> findTop20ByWorkspaceIdOrderByStartedAtDesc(UUID workspaceId);

    Optional<AdBrainRunSummary> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
