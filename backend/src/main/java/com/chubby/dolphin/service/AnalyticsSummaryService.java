package com.chubby.dolphin.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsSummaryService {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsSummaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary summary(String workspaceId) {
        UUID workspaceUuid = UUID.fromString(workspaceId);

        CampaignSummary campaignSummary = campaignSummary(workspaceId);
        LeadSummary leadSummary = leadSummary(workspaceId);
        ApprovalSummary approvalSummary = approvalSummary(workspaceUuid);
        ContentFactorySummary contentFactorySummary = contentFactorySummary(workspaceUuid);
        AdBrainSummary adBrainSummary = adBrainSummary(workspaceUuid);
        RiskOpportunitySummary riskOpportunitySummary = riskOpportunitySummary(workspaceUuid);

        boolean empty = campaignSummary.empty()
                && leadSummary.empty()
                && approvalSummary.empty()
                && contentFactorySummary.empty()
                && adBrainSummary.empty()
                && riskOpportunitySummary.empty();

        return new AnalyticsSummary(
                workspaceId,
                LocalDateTime.now(),
                campaignSummary,
                leadSummary,
                approvalSummary,
                contentFactorySummary,
                adBrainSummary,
                riskOpportunitySummary,
                new EmptyState(
                        empty,
                        empty
                                ? "No analytics data exists yet for this workspace."
                                : "Analytics is based only on recorded workspace data."
                ),
                true
        );
    }

    private CampaignSummary campaignSummary(String workspaceId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active,
                    COUNT(*) FILTER (WHERE status = 'PAUSED') AS paused,
                    COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                    COALESCE(SUM(budget), 0) AS total_budget,
                    COALESCE(SUM(spent), 0) AS total_spend,
                    COALESCE(SUM(CASE WHEN spent IS NOT NULL AND roas IS NOT NULL THEN spent * roas ELSE 0 END), 0) AS recorded_attributed_revenue,
                    COALESCE(AVG(roas) FILTER (WHERE roas IS NOT NULL), 0) AS average_roas,
                    COALESCE(AVG(cpl) FILTER (WHERE cpl IS NOT NULL), 0) AS average_cpl
                FROM campaigns
                WHERE account_id = ?
                """, workspaceId);

        long total = longValue(row.get("total"));
        return new CampaignSummary(
                total,
                longValue(row.get("active")),
                longValue(row.get("paused")),
                longValue(row.get("completed")),
                doubleValue(row.get("total_budget")),
                doubleValue(row.get("total_spend")),
                doubleValue(row.get("recorded_attributed_revenue")),
                round(doubleValue(row.get("average_roas"))),
                round(doubleValue(row.get("average_cpl"))),
                "campaigns",
                total == 0
        );
    }

    private LeadSummary leadSummary(String workspaceId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status = 'NEW') AS new_leads,
                    COUNT(*) FILTER (WHERE temperature = 'HOT') AS hot,
                    COUNT(*) FILTER (WHERE temperature = 'WARM') AS warm,
                    COUNT(*) FILTER (WHERE temperature = 'COLD') AS cold,
                    COUNT(*) FILTER (WHERE temperature IS NULL OR temperature = 'UNKNOWN') AS unknown_temperature,
                    COALESCE(AVG(score) FILTER (WHERE score IS NOT NULL), 0) AS average_score
                FROM leads
                WHERE account_id = ?
                """, workspaceId);

        long total = longValue(row.get("total"));
        return new LeadSummary(
                total,
                longValue(row.get("new_leads")),
                longValue(row.get("hot")),
                longValue(row.get("warm")),
                longValue(row.get("cold")),
                longValue(row.get("unknown_temperature")),
                round(doubleValue(row.get("average_score"))),
                "leads",
                total == 0
        );
    }

    private ApprovalSummary approvalSummary(UUID workspaceId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status = 'PENDING') AS pending,
                    COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
                    COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected,
                    COUNT(*) FILTER (WHERE requires_execution = true) AS requires_execution
                FROM approval_items
                WHERE workspace_id = ?
                """, workspaceId);

        long total = longValue(row.get("total"));
        return new ApprovalSummary(
                total,
                longValue(row.get("pending")),
                longValue(row.get("approved")),
                longValue(row.get("rejected")),
                longValue(row.get("requires_execution")),
                "approval_items",
                total == 0
        );
    }

    private ContentFactorySummary contentFactorySummary(UUID workspaceId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    (SELECT COUNT(*) FROM content_factory_items WHERE workspace_id = ?) AS items,
                    COUNT(*) AS variants,
                    COUNT(*) FILTER (WHERE approval_status = 'DRAFT') AS draft_variants,
                    COUNT(*) FILTER (WHERE approval_status = 'SUBMITTED_FOR_APPROVAL') AS submitted_variants,
                    COUNT(*) FILTER (WHERE approval_status = 'APPROVED') AS approved_variants,
                    COALESCE(AVG(score) FILTER (WHERE score IS NOT NULL), 0) AS average_score
                FROM content_factory_variants
                WHERE workspace_id = ?
                """, workspaceId, workspaceId);

        long items = longValue(row.get("items"));
        long variants = longValue(row.get("variants"));
        return new ContentFactorySummary(
                items,
                variants,
                longValue(row.get("draft_variants")),
                longValue(row.get("submitted_variants")),
                longValue(row.get("approved_variants")),
                round(doubleValue(row.get("average_score"))),
                List.of("content_factory_items", "content_factory_variants"),
                items == 0 && variants == 0
        );
    }

    private AdBrainSummary adBrainSummary(UUID workspaceId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS runs,
                    MAX(started_at) AS latest_run_at,
                    COALESCE(SUM(campaigns_evaluated), 0) AS campaigns_evaluated,
                    COALESCE(SUM(evaluations_created), 0) AS evaluations_created,
                    COALESCE(SUM(approval_items_created), 0) AS approvals_created,
                    COALESCE(SUM(duplicate_approvals_skipped), 0) AS duplicate_approvals_skipped,
                    COALESCE(SUM(risks_created), 0) AS risks_created,
                    COALESCE(SUM(opportunities_created), 0) AS opportunities_created
                FROM ad_brain_runs
                WHERE workspace_id = ?
                """, workspaceId);

        long runs = longValue(row.get("runs"));
        return new AdBrainSummary(
                runs,
                localDateTime(row.get("latest_run_at")),
                longValue(row.get("campaigns_evaluated")),
                longValue(row.get("evaluations_created")),
                longValue(row.get("approvals_created")),
                longValue(row.get("duplicate_approvals_skipped")),
                longValue(row.get("risks_created")),
                longValue(row.get("opportunities_created")),
                "ad_brain_runs",
                runs == 0
        );
    }

    private RiskOpportunitySummary riskOpportunitySummary(UUID workspaceId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS math_evaluations,
                    COUNT(*) FILTER (WHERE requires_approval = true) AS requires_approval,
                    COUNT(*) FILTER (WHERE severity = 'CRITICAL') AS critical,
                    COUNT(*) FILTER (WHERE severity = 'HIGH') AS high,
                    COUNT(*) FILTER (WHERE status = 'NOT_ENOUGH_DATA') AS not_enough_data,
                    MAX(created_at) AS latest_evaluation_at
                FROM campaign_math_evaluations
                WHERE workspace_id = ?
                """, workspaceId);

        long evaluations = longValue(row.get("math_evaluations"));
        return new RiskOpportunitySummary(
                evaluations,
                longValue(row.get("requires_approval")),
                longValue(row.get("critical")),
                longValue(row.get("high")),
                longValue(row.get("not_enough_data")),
                localDateTime(row.get("latest_evaluation_at")),
                "campaign_math_evaluations",
                evaluations == 0
        );
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
    }

    public record AnalyticsSummary(
            String workspaceId,
            LocalDateTime generatedAt,
            CampaignSummary campaignSummary,
            LeadSummary leadSummary,
            ApprovalSummary approvalSummary,
            ContentFactorySummary contentFactorySummary,
            AdBrainSummary adBrainSummary,
            RiskOpportunitySummary riskOpportunitySummary,
            EmptyState emptyState,
            boolean readOnly
    ) {
    }

    public record CampaignSummary(
            long total,
            long active,
            long paused,
            long completed,
            double totalBudget,
            double totalSpend,
            double recordedAttributedRevenue,
            double averageRoas,
            double averageCpl,
            String sourceTable,
            boolean empty
    ) {
    }

    public record LeadSummary(
            long total,
            long newLeads,
            long hot,
            long warm,
            long cold,
            long unknownTemperature,
            double averageScore,
            String sourceTable,
            boolean empty
    ) {
    }

    public record ApprovalSummary(
            long total,
            long pending,
            long approved,
            long rejected,
            long requiresExecution,
            String sourceTable,
            boolean empty
    ) {
    }

    public record ContentFactorySummary(
            long items,
            long variants,
            long draftVariants,
            long submittedVariants,
            long approvedVariants,
            double averageScore,
            List<String> sourceTables,
            boolean empty
    ) {
    }

    public record AdBrainSummary(
            long runs,
            LocalDateTime latestRunAt,
            long campaignsEvaluated,
            long evaluationsCreated,
            long approvalsCreated,
            long duplicateApprovalsSkipped,
            long risksCreated,
            long opportunitiesCreated,
            String sourceTable,
            boolean empty
    ) {
    }

    public record RiskOpportunitySummary(
            long mathEvaluations,
            long requiresApproval,
            long critical,
            long high,
            long notEnoughData,
            LocalDateTime latestEvaluationAt,
            String sourceTable,
            boolean empty
    ) {
    }

    public record EmptyState(boolean isEmpty, String message) {
    }
}
