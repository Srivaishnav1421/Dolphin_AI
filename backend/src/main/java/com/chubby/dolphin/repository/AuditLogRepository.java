package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findTop100ByOrderByTimestampDesc();
    List<AuditLog> findTop100ByOrganizationIdOrderByTimestampDesc(String organizationId);
    List<AuditLog> findTop100ByWorkspaceIdOrderByTimestampDesc(String workspaceId);
    List<AuditLog> findByUserEmailOrderByTimestampDesc(String email);
    List<AuditLog> findTop100ByWorkspaceIdAndActorTypeOrderByTimestampDesc(String workspaceId, String actorType);
}
