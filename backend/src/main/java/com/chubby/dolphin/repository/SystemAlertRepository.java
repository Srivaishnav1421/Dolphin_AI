package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.SystemAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemAlertRepository extends JpaRepository<SystemAlert, String> {
    List<SystemAlert> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);
    List<SystemAlert> findByWorkspaceIdAndResolvedOrderByCreatedAtDesc(String workspaceId, Boolean resolved);
}
