package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.SubscriptionEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubscriptionEntitlementRepository extends JpaRepository<SubscriptionEntitlement, String> {
    List<SubscriptionEntitlement> findByPlanId(String planId);
}
