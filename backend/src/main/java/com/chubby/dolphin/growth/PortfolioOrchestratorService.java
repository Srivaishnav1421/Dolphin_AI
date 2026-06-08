package com.chubby.dolphin.growth;

import com.chubby.dolphin.entity.Workspace;
import com.chubby.dolphin.entity.User;
import com.chubby.dolphin.growth.dto.ClvForecast;
import com.chubby.dolphin.growth.dto.PortfolioInsight;
import com.chubby.dolphin.repository.WorkspaceRepository;
import com.chubby.dolphin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioOrchestratorService {

    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceHealthEngine healthEngine;
    private final ChurnPredictionEngine churnEngine;
    private final ClvForecastEngine clvEngine;
    private final SecurityUtils sec;

    public List<PortfolioInsight> orchestratePortfolio() {
        log.info("🌐 Initiating multi-workspace portfolio orchestration...");
        List<Workspace> workspaces;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            boolean isSystemAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_SYSTEM_ADMIN".equals(a.getAuthority()));
            if (isSystemAdmin) {
                workspaces = workspaceRepo.findAll();
            } else {
                User currentUser = sec.currentUser();
                if (currentUser.getOrganization() != null) {
                    workspaces = workspaceRepo.findByOrganizationId(currentUser.getOrganization().getId());
                } else {
                    String activeId = sec.currentWorkspaceId();
                    if (activeId != null && !activeId.isBlank()) {
                        workspaces = workspaceRepo.findById(activeId)
                                .map(List::of)
                                .orElse(List.of());
                    } else {
                        workspaces = new ArrayList<>();
                    }
                }
            }
        } else {
            // Background thread (GrowthLoop scheduler)
            workspaces = workspaceRepo.findAll();
        }

        List<PortfolioInsight> insights = new ArrayList<>();

        for (Workspace ws : workspaces) {
            double healthScore = healthEngine.calculateHealthScore(ws.getId());
            String classification = healthEngine.getClassification(healthScore).name();
            double churnRisk = churnEngine.predictChurn(ws.getId()).getChurnProbability();
            ClvForecast clv = clvEngine.forecastClv(ws.getId());

            double priorityScore = (churnRisk * 0.4) + (clv.getGrowthPotential() * 0.3) + ((100.0 - healthScore) * 0.3);
            boolean isNeglected = healthScore < 60.0 && churnRisk > 50.0;
            double resourceScore = Math.clamp(priorityScore * 1.2, 10.0, 100.0);

            insights.add(PortfolioInsight.builder()
                    .workspaceId(ws.getId())
                    .workspaceName(ws.getName())
                    .healthScore(healthScore)
                    .healthClassification(classification)
                    .churnRisk(churnRisk)
                    .clvForecast(clv)
                    .priorityScore(priorityScore)
                    .isNeglected(isNeglected)
                    .resourceAllocationScore(resourceScore)
                    .build());
        }

        insights.sort(Comparator.comparingDouble(PortfolioInsight::getPriorityScore).reversed());
        return insights;
    }
}
