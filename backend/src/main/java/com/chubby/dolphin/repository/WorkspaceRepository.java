package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, String> {
    List<Workspace> findByOrganizationId(String organizationId);
}
