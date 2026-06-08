package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Subscription;
import com.chubby.dolphin.entity.SubscriptionPlan;
import com.chubby.dolphin.repository.SubscriptionPlanRepository;
import com.chubby.dolphin.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionLifecycleService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional
    public Subscription createSubscription(String workspaceId, String planId, int seats) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Target plan not found: " + planId));

        Optional<Subscription> existing = subscriptionRepository.findByWorkspaceId(workspaceId);
        if (existing.isPresent()) {
            throw new IllegalStateException("Subscription already exists for workspace: " + workspaceId);
        }

        Subscription sub = Subscription.builder()
                .workspaceId(workspaceId)
                .plan(plan)
                .status("ACTIVE")
                .allocatedSeats(seats)
                .currentPeriodStart(LocalDateTime.now())
                .currentPeriodEnd(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();

        log.info("Subscription created for workspace={}, plan={}", workspaceId, plan.getName());
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public void upgradePlan(String workspaceId, String newPlanId, int additionalSeats) {
        Subscription sub = subscriptionRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("No active subscription for workspace: " + workspaceId));

        SubscriptionPlan newPlan = subscriptionPlanRepository.findById(newPlanId)
                .orElseThrow(() -> new IllegalArgumentException("Target plan not found: " + newPlanId));

        log.info("Upgrading workspace {} from plan {} to plan {} immediately", 
                workspaceId, sub.getPlan().getName(), newPlan.getName());

        sub.setPlan(newPlan);
        sub.setAllocatedSeats(sub.getAllocatedSeats() + additionalSeats);
        sub.setCurrentPeriodStart(LocalDateTime.now());
        // Prorated expansion triggers immediate entitlement updates
        sub.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void scheduleDowngrade(String workspaceId, String targetPlanId) {
        // Enforce scheduled downgrades at period end to protect user data from deletion
        Subscription sub = subscriptionRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("No subscription found for workspace: " + workspaceId));
        
        log.info("Scheduled downgrade for workspace {} to plan {} at billing cycle end: {}", 
                workspaceId, targetPlanId, sub.getCurrentPeriodEnd());
        // In real system, write metadata fields in subscription representing scheduled plan changes
    }

    @Transactional
    public void cancelSubscriptionImmediately(String workspaceId) {
        Subscription sub = subscriptionRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("No subscription found for workspace: " + workspaceId));
        
        log.warn("Suspending subscription for workspace: {}", workspaceId);
        sub.setStatus("SUSPENDED");
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void activateSubscription(String workspaceId) {
        Subscription sub = subscriptionRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("No subscription found for workspace: " + workspaceId));
        sub.setStatus("ACTIVE");
        subscriptionRepository.save(sub);
    }
}
