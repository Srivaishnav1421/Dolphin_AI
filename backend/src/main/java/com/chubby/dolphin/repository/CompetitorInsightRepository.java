package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.CompetitorInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetitorInsightRepository extends JpaRepository<CompetitorInsight, String> {
    List<CompetitorInsight> findByAccountId(String accountId);
    List<CompetitorInsight> findByAccountIdAndCompetitorUrl(String accountId, String competitorUrl);
}
