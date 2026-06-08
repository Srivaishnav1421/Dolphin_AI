package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WorkspaceAiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspaceAiConfigRepository extends JpaRepository<WorkspaceAiConfig, String> {

    Optional<WorkspaceAiConfig> findByWorkspaceId(String workspaceId);
}
