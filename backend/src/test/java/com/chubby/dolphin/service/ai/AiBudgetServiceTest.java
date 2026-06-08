package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.entity.AiWorkspaceBudget;
import com.chubby.dolphin.repository.AiUsageLogRepository;
import com.chubby.dolphin.repository.AiWorkspaceBudgetRepository;
import com.chubby.dolphin.service.ai.audit.AiBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AiBudgetServiceTest {

    @Mock
    private AiWorkspaceBudgetRepository budgetRepo;

    @Mock
    private AiUsageLogRepository usageLogRepo;

    private AiBudgetService budgetService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        budgetService = new AiBudgetService(budgetRepo, usageLogRepo);
    }

    @Test
    public void testWithinBudgetReturnsTrueWhenNoBudgetRecordExists() {
        when(budgetRepo.findByWorkspaceId("ws-empty")).thenReturn(Optional.empty());

        boolean result = budgetService.withinBudget("ws-empty");
        assertTrue(result);
        verify(usageLogRepo, never()).sumCostByWorkspaceIdSince(anyString(), any(LocalDateTime.class));
    }

    @Test
    public void testWithinBudgetReturnsTrueWhenSpendIsBelowLimit() {
        AiWorkspaceBudget budget = AiWorkspaceBudget.builder()
                .workspaceId("ws-1")
                .monthlyUsdBudget(100.0)
                .hardStopEnabled(true)
                .build();

        when(budgetRepo.findByWorkspaceId("ws-1")).thenReturn(Optional.of(budget));
        // Current spend is only $45.0 USD
        when(usageLogRepo.sumCostByWorkspaceIdSince(eq("ws-1"), any(LocalDateTime.class))).thenReturn(45.0);

        boolean result = budgetService.withinBudget("ws-1");
        assertTrue(result);
    }

    @Test
    public void testWithinBudgetThrowsExceptionWhenSpendExceedsAndHardStopEnabled() {
        AiWorkspaceBudget budget = AiWorkspaceBudget.builder()
                .workspaceId("ws-1")
                .monthlyUsdBudget(50.0)
                .hardStopEnabled(true)
                .build();

        when(budgetRepo.findByWorkspaceId("ws-1")).thenReturn(Optional.of(budget));
        // Current spend is $55.0 USD (exceeded by $5)
        when(usageLogRepo.sumCostByWorkspaceIdSince(eq("ws-1"), any(LocalDateTime.class))).thenReturn(55.0);

        assertThrows(IllegalStateException.class, () -> {
            budgetService.withinBudget("ws-1");
        });
    }

    @Test
    public void testWithinBudgetReturnsTrueWhenSpendExceedsButHardStopDisabled() {
        AiWorkspaceBudget budget = AiWorkspaceBudget.builder()
                .workspaceId("ws-1")
                .monthlyUsdBudget(50.0)
                .hardStopEnabled(false) // Hard stop is disabled!
                .build();

        when(budgetRepo.findByWorkspaceId("ws-1")).thenReturn(Optional.of(budget));
        // Current spend is $55.0 USD
        when(usageLogRepo.sumCostByWorkspaceIdSince(eq("ws-1"), any(LocalDateTime.class))).thenReturn(55.0);

        boolean result = budgetService.withinBudget("ws-1");
        assertTrue(result); // Should not throw!
    }
}
