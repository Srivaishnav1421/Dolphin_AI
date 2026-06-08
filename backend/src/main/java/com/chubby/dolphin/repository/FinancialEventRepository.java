package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.FinancialEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FinancialEventRepository extends JpaRepository<FinancialEvent, String> {
    List<FinancialEvent> findByWorkspaceId(String workspaceId);
}
