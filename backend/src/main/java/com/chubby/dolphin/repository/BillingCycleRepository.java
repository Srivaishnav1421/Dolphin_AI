package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.BillingCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BillingCycleRepository extends JpaRepository<BillingCycle, String> {
    List<BillingCycle> findByWorkspaceId(String workspaceId);
    Optional<BillingCycle> findByWorkspaceIdAndStatus(String workspaceId, String status);
}
