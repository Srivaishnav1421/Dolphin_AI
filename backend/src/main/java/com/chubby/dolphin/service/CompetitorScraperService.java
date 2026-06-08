package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.CompetitorInsight;
import java.util.List;

public interface CompetitorScraperService {
    /**
     * Crawls and extracts competitor hooks, demographics, value propositions and pricing models.
     */
    CompetitorInsight analyzeCompetitor(String competitorUrl, String accountId);

    /**
     * Retrieves historically stored competitor insights for an account.
     */
    List<CompetitorInsight> getInsightsForAccount(String accountId);
}
