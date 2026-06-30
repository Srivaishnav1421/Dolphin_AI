package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WorkspaceAiTaskRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceAiTaskRouteRepository extends JpaRepository<WorkspaceAiTaskRoute, String> {
    List<WorkspaceAiTaskRoute> findAllByWorkspaceId(String workspaceId);
    Optional<WorkspaceAiTaskRoute> findByWorkspaceIdAndTaskKey(String workspaceId, String taskKey);
    void deleteByWorkspaceIdAndTaskKey(String workspaceId, String taskKey);
}
