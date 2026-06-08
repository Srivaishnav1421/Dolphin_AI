package com.chubby.dolphin.service.ai.audit;

import com.chubby.dolphin.entity.AiWorkspaceBudget;
import com.chubby.dolphin.repository.AiUsageLogRepository;
import com.chubby.dolphin.repository.AiWorkspaceBudgetRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AiBudgetService {

    private final AiWorkspaceBudgetRepository budgetRepo;
    private final AiUsageLogRepository usageLogRepo;

    public AiBudgetService(AiWorkspaceBudgetRepository budgetRepo, AiUsageLogRepository usageLogRepo) {
        this.budgetRepo = budgetRepo;
        this.usageLogRepo = usageLogRepo;
    }

    /**
     * Checks if a workspace is within budget. Blocks execution by throwing exception if exceeded.
     */
    public boolean withinBudget(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return true;
        }

        Optional<AiWorkspaceBudget> budgetOpt = budgetRepo.findByWorkspaceId(workspaceId);
        if (budgetOpt.isEmpty()) {
            return true; // No custom budget constraints set
        }

        AiWorkspaceBudget budget = budgetOpt.get();

        // 1. Gather all logged costs since the start of this month
        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        double currentMonthSpend = usageLogRepo.sumCostByWorkspaceIdSince(workspaceId, startOfMonth);

        // 2. Enforce limits
        if (Boolean.TRUE.equals(budget.getHardStopEnabled())) {
            if (currentMonthSpend >= budget.getMonthlyUsdBudget()) {
                throw new IllegalStateException("Workspace AI budget exceeded");
            }
        }

        return true;
    }
}
