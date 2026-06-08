package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.PlanOverride;
import com.chubby.dolphin.entity.Subscription;
import com.chubby.dolphin.entity.SubscriptionEntitlement;
import com.chubby.dolphin.repository.PlanOverrideRepository;
import com.chubby.dolphin.repository.SubscriptionEntitlementRepository;
import com.chubby.dolphin.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementsEngine {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEntitlementRepository entitlementRepository;
    private final PlanOverrideRepository planOverrideRepository;
    private final UsageMeteringService usageMeteringService;

    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String workspaceId, String featureKey) {
        // 1. Check for custom overrides (DA-036)
        Optional<PlanOverride> overrideOpt = planOverrideRepository.findByWorkspaceIdAndFeatureKey(workspaceId, featureKey);
        if (overrideOpt.isPresent()) {
            return overrideOpt.get().isEnabled();
        }

        // 2. Fetch active plan entitlements
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByWorkspaceId(workspaceId);
        if (subscriptionOpt.isEmpty()) {
            log.warn("No subscription found for workspace: {}. Access denied.", workspaceId);
            return false;
        }

        Subscription sub = subscriptionOpt.get();
        if (!"ACTIVE".equalsIgnoreCase(sub.getStatus())) {
            log.warn("Subscription for workspace {} is not active (status: {}). Access denied.", workspaceId, sub.getStatus());
            return false;
        }

        return entitlementRepository.findByPlanId(sub.getPlan().getId()).stream()
                .filter(ent -> ent.getFeatureKey().equalsIgnoreCase(featureKey))
                .findFirst()
                .map(SubscriptionEntitlement::isEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean checkLimit(String workspaceId, String featureKey, String metricName, long requestedUnits) {
        // 1. Resolve effective limit
        long limit = getEffectiveLimit(workspaceId, featureKey);
        if (limit == -1) {
            return true; // Unlimited
        }

        // 2. Resolve current usage
        long currentUsage;
        if ("ai_credits".equalsIgnoreCase(metricName)) {
            currentUsage = (long) usageMeteringService.getSummedCredits(workspaceId);
        } else {
            currentUsage = usageMeteringService.getSummedUnits(workspaceId, metricName);
        }

        return (currentUsage + requestedUnits) <= limit;
    }

    private long getEffectiveLimit(String workspaceId, String featureKey) {
        // Check plan overrides first (DA-036)
        Optional<PlanOverride> overrideOpt = planOverrideRepository.findByWorkspaceIdAndFeatureKey(workspaceId, featureKey);
        if (overrideOpt.isPresent()) {
            PlanOverride override = overrideOpt.get();
            if (!override.isEnabled()) {
                return 0; // Feature is turned off
            }
            return override.getLimitValue() != null ? override.getLimitValue() : -1;
        }

        // Fallback to plan base entitlements
        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByWorkspaceId(workspaceId);
        if (subscriptionOpt.isEmpty()) {
            return 0;
        }

        Subscription sub = subscriptionOpt.get();
        return entitlementRepository.findByPlanId(sub.getPlan().getId()).stream()
                .filter(ent -> ent.getFeatureKey().equalsIgnoreCase(featureKey))
                .findFirst()
                .map(ent -> ent.getLimitValue() != null ? ent.getLimitValue() : -1)
                .orElse(0);
    }
}
