package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UsageEventRepository extends JpaRepository<UsageEvent, String> {
    List<UsageEvent> findByWorkspaceIdAndBillingCycleId(String workspaceId, String billingCycleId);

    @Query("SELECT COALESCE(SUM(u.units), 0) FROM UsageEvent u WHERE u.workspaceId = :workspaceId AND u.billingCycle.id = :cycleId AND u.metricName = :metricName")
    long sumUnitsByWorkspaceAndCycleAndMetric(
            @Param("workspaceId") String workspaceId,
            @Param("cycleId") String cycleId,
            @Param("metricName") String metricName
    );

    @Query("SELECT COALESCE(SUM(u.creditsConsumed), 0.0) FROM UsageEvent u WHERE u.workspaceId = :workspaceId AND u.billingCycle.id = :cycleId AND u.metricName = :metricName")
    double sumCreditsByWorkspaceAndCycleAndMetric(
            @Param("workspaceId") String workspaceId,
            @Param("cycleId") String cycleId,
            @Param("metricName") String metricName
    );
}
