package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, String> {
    List<MetricSnapshot> findByCampaignIdOrderBySnapshotDateDesc(String campaignId);
    List<MetricSnapshot> findByCampaignIdAndSnapshotDateBetween(String campaignId, LocalDate start, LocalDate end);
    Optional<MetricSnapshot> findByCampaignIdAndSnapshotDate(String campaignId, LocalDate date);
    List<MetricSnapshot> findByAccountIdAndSnapshotDate(String accountId, LocalDate date);
    List<MetricSnapshot> findByAccountId(String accountId);

    @Query("SELECT m FROM MetricSnapshot m WHERE m.accountId = :accountId " +
           "AND m.snapshotDate BETWEEN :start AND :end ORDER BY m.snapshotDate")
    List<MetricSnapshot> findByAccountIdAndDateRange(
        @Param("accountId") String accountId,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("SELECT m FROM MetricSnapshot m WHERE m.campaignId = :campaignId " +
           "ORDER BY m.snapshotDate DESC LIMIT 7")
    List<MetricSnapshot> findLast7DaysByCampaignId(@Param("campaignId") String campaignId);
}
