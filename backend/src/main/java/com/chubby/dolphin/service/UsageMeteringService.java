package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.BillingCycle;
import com.chubby.dolphin.entity.UsageEvent;
import com.chubby.dolphin.repository.BillingCycleRepository;
import com.chubby.dolphin.repository.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageMeteringService {

    private final UsageEventRepository usageEventRepository;
    private final BillingCycleRepository billingCycleRepository;

    @Transactional
    public BillingCycle getOrCreateCurrentCycle(String workspaceId) {
        return billingCycleRepository.findByWorkspaceIdAndStatus(workspaceId, "CURRENT")
                .orElseGet(() -> {
                    LocalDateTime start = LocalDateTime.now();
                    LocalDateTime end = start.plusDays(30);
                    BillingCycle cycle = BillingCycle.builder()
                            .workspaceId(workspaceId)
                            .periodStart(start)
                            .periodEnd(end)
                            .status("CURRENT")
                            .createdAt(LocalDateTime.now())
                            .build();
                    return billingCycleRepository.save(cycle);
                });
    }

    @Transactional
    public void recordUsage(String workspaceId, String metricName, int units, String source, String resType, String resId) {
        BillingCycle cycle = getOrCreateCurrentCycle(workspaceId);
        UsageEvent event = UsageEvent.builder()
                .workspaceId(workspaceId)
                .billingCycle(cycle)
                .metricName(metricName)
                .units(units)
                .eventSource(source)
                .resourceType(resType)
                .resourceId(resId)
                .billable(true)
                .createdAt(LocalDateTime.now())
                .build();
        usageEventRepository.save(event);
        log.info("Recorded usage event: workspace={}, metric={}, units={}", workspaceId, metricName, units);
    }

    @Transactional
    public void recordAiUsage(String workspaceId, String provider, String model, double credits, double estCost, int tokens, String source) {
        BillingCycle cycle = getOrCreateCurrentCycle(workspaceId);
        UsageEvent event = UsageEvent.builder()
                .workspaceId(workspaceId)
                .billingCycle(cycle)
                .metricName("ai_credits")
                .units(tokens)
                .eventSource(source)
                .provider(provider)
                .model(model)
                .creditsConsumed(credits)
                .estimatedCostInr(estCost)
                .billable(true)
                .createdAt(LocalDateTime.now())
                .build();
        usageEventRepository.save(event);
        log.info("Recorded AI usage: workspace={}, provider={}, model={}, credits={}, cost_inr={}",
                workspaceId, provider, model, credits, estCost);
    }

    @Transactional(readOnly = true)
    public long getSummedUnits(String workspaceId, String metricName) {
        BillingCycle cycle = billingCycleRepository.findByWorkspaceIdAndStatus(workspaceId, "CURRENT").orElse(null);
        if (cycle == null) {
            return 0;
        }
        return usageEventRepository.sumUnitsByWorkspaceAndCycleAndMetric(workspaceId, cycle.getId(), metricName);
    }

    @Transactional(readOnly = true)
    public double getSummedCredits(String workspaceId) {
        BillingCycle cycle = billingCycleRepository.findByWorkspaceIdAndStatus(workspaceId, "CURRENT").orElse(null);
        if (cycle == null) {
            return 0.0;
        }
        return usageEventRepository.sumCreditsByWorkspaceAndCycleAndMetric(workspaceId, cycle.getId(), "ai_credits");
    }
}
