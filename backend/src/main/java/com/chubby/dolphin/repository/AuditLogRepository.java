package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findTop100ByOrderByTimestampDesc();
    List<AuditLog> findByUserEmailOrderByTimestampDesc(String email);
}
