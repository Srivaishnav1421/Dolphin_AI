package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.PlanOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlanOverrideRepository extends JpaRepository<PlanOverride, String> {
    List<PlanOverride> findByWorkspaceId(String workspaceId);
    Optional<PlanOverride> findByWorkspaceIdAndFeatureKey(String workspaceId, String featureKey);
}
