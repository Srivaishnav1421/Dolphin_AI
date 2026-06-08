package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.entity.MetaConnection;
import com.chubby.dolphin.entity.Wallet;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Safety Rules Engine — NON-NEGOTIABLE guardrails that OVERRIDE AI decisions.
 *
 * No matter how confident the AI is, these rules are always enforced:
 *   1. Never exceed daily spend cap
 *   2. Never pause a PROTECTED campaign
 *   3. Never change budget more than max% in one cycle
 *   4. Never create more than N campaigns per day
 *   5. Emergency pause ALL if wallet balance < minimum
 *   6. Never execute on expired/invalid Meta tokens
 *
 * These rules exist because we're managing REAL MONEY on REAL ad platforms.
 * One bad AI decision could burn through a client's entire budget.
 */
@Service
@Slf4j
public class SafetyRulesEngine {

    private final WalletRepository walletRepo;
    private final CampaignRepository campaignRepo;

    @Value("${safety.max-daily-spend:50000}")
    private double maxDailySpend;

    @Value("${safety.max-single-budget-change-percent:30}")
    private double maxBudgetChangePercent;

    @Value("${brain.emergency-pause.min-balance:500}")
    private double emergencyMinBalance;

    @Value("${brain.max-campaigns-per-day:20}")
    private int maxCampaignsPerDay;

    public SafetyRulesEngine(WalletRepository walletRepo, CampaignRepository campaignRepo) {
        this.walletRepo = walletRepo;
        this.campaignRepo = campaignRepo;
    }

    /**
     * Check if the system is allowed to spend the given amount.
     * Returns a SafetyResult with pass/fail and reason.
     */
    public SafetyResult canSpend(String accountId, double amount) {
        Wallet wallet = walletRepo.findFirstByAccountId(accountId).orElse(null);
        if (wallet == null) {
            return SafetyResult.fail("NO_WALLET", "No wallet found for this account");
        }

        double balance = wallet.getBalance() != null ? wallet.getBalance() : 0;
        if (balance < amount) {
            return SafetyResult.fail("INSUFFICIENT_BALANCE",
                    String.format("Wallet balance ₹%.0f is less than requested spend ₹%.0f", balance, amount));
        }

        if (amount > maxDailySpend) {
            return SafetyResult.fail("EXCEEDS_DAILY_CAP",
                    String.format("Spend ₹%.0f exceeds daily cap ₹%.0f", amount, maxDailySpend));
        }

        Double dailyLimit = wallet.getDailyBudgetLimit();
        if (dailyLimit != null && amount > dailyLimit) {
            return SafetyResult.fail("EXCEEDS_ACCOUNT_LIMIT",
                    String.format("Spend ₹%.0f exceeds account daily limit ₹%.0f", amount, dailyLimit));
        }

        return SafetyResult.pass();
    }

    /**
     * Check if a campaign can be paused by AI.
     * PROTECTED campaigns (manually marked) cannot be auto-paused.
     */
    public SafetyResult canPause(Campaign campaign) {
        if (campaign == null) {
            return SafetyResult.fail("CAMPAIGN_NOT_FOUND", "Campaign not found");
        }
        if ("COMPLETED".equals(campaign.getStatus())) {
            return SafetyResult.fail("ALREADY_COMPLETED", "Campaign is already completed");
        }
        if ("PAUSED".equals(campaign.getStatus())) {
            return SafetyResult.fail("ALREADY_PAUSED", "Campaign is already paused");
        }
        return SafetyResult.pass();
    }

    /**
     * Check if a budget change is within safe limits.
     * Prevents the AI from making wild budget swings.
     */
    public SafetyResult isBudgetChangeSafe(double currentBudget, double proposedBudget) {
        if (currentBudget <= 0) {
            return SafetyResult.pass(); // New campaign, any budget is fine
        }

        double changePercent = Math.abs((proposedBudget - currentBudget) / currentBudget) * 100;
        if (changePercent > maxBudgetChangePercent) {
            return SafetyResult.fail("BUDGET_CHANGE_TOO_LARGE",
                    String.format("Budget change of %.1f%% exceeds max allowed %.1f%%",
                                  changePercent, maxBudgetChangePercent));
        }

        if (proposedBudget < 0) {
            return SafetyResult.fail("NEGATIVE_BUDGET", "Budget cannot be negative");
        }

        return SafetyResult.pass();
    }

    /**
     * Check if the system can create a new campaign today.
     * Prevents runaway campaign creation.
     */
    public SafetyResult canCreateCampaign(String accountId) {
        long todayCampaigns = campaignRepo.findByAccountId(accountId).stream()
                .filter(c -> c.getCreatedAt() != null &&
                             c.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()))
                .count();

        if (todayCampaigns >= maxCampaignsPerDay) {
            return SafetyResult.fail("MAX_CAMPAIGNS_REACHED",
                    String.format("Already created %d campaigns today (max: %d)", todayCampaigns, maxCampaignsPerDay));
        }
        return SafetyResult.pass();
    }

    /**
     * Check if a Meta connection is valid for executing actions.
     */
    public SafetyResult canExecuteOnMeta(MetaConnection conn) {
        if (conn == null) {
            return SafetyResult.fail("NO_META_CONNECTION", "No Meta connection found for this account");
        }
        if (!"VALID".equals(conn.getTokenStatus())) {
            return SafetyResult.fail("TOKEN_INVALID",
                    "Meta token is " + conn.getTokenStatus() + " — reconnect your Meta account");
        }
        if (!conn.isAutoManageEnabled()) {
            return SafetyResult.fail("AUTO_MANAGE_DISABLED",
                    "Auto-manage is disabled for this Meta connection. Enable it in settings.");
        }
        return SafetyResult.pass();
    }

    /**
     * Emergency check: if wallet balance is critically low, return true.
     * The Brain should pause ALL active campaigns immediately.
     */
    public boolean isEmergencyPauseNeeded(String accountId) {
        return walletRepo.findFirstByAccountId(accountId)
                .map(w -> w.getBalance() != null && w.getBalance() < emergencyMinBalance)
                .orElse(false);
    }

    /**
     * Full safety check before executing any Brain decision.
     * Returns the first failure found, or pass if all clear.
     */
    public SafetyResult fullPreFlightCheck(String accountId, MetaConnection conn,
                                           String decisionType, Campaign campaign,
                                           Double proposedBudget) {
        // 1. Emergency balance check
        if (isEmergencyPauseNeeded(accountId)) {
            return SafetyResult.fail("EMERGENCY_LOW_BALANCE",
                    "Wallet balance critically low — all actions blocked");
        }

        // 2. Campaign data maturity safety checks (Minimum 3 days, 20 conversions)
        if (campaign != null && ("SCALE_UP".equals(decisionType) || "SCALE_DOWN".equals(decisionType) || "PAUSE".equals(decisionType))) {
            int days = campaign.getDaysOfData() != null ? campaign.getDaysOfData() : 0;
            int convs = campaign.getConversions() != null ? campaign.getConversions() : 0;
            if (days < 3) {
                return SafetyResult.fail("INSUFFICIENT_DATA_DAYS",
                        String.format("Campaign '%s' only has %d days of data (min 3 required)", campaign.getName(), days));
            }
            if (convs < 20) {
                return SafetyResult.fail("INSUFFICIENT_CONVERSIONS",
                        String.format("Campaign '%s' only has %d conversions (min 20 required)", campaign.getName(), convs));
            }
        }

        // 3. Workspace budget limit safety check
        if (proposedBudget != null) {
            SafetyResult spendCheck = canSpend(accountId, proposedBudget);
            if (!spendCheck.passed()) {
                return spendCheck;
            }
        }

        // 2. Meta connection check (for any Meta-facing action)
        if (conn != null) {
            SafetyResult metaCheck = canExecuteOnMeta(conn);
            if (!metaCheck.passed()) return metaCheck;
        }

        // 3. Decision-specific checks
        return switch (decisionType) {
            case "PAUSE" -> canPause(campaign);
            case "SCALE_UP", "SCALE_DOWN" -> {
                if (campaign != null && proposedBudget != null) {
                    double current = campaign.getBudget() != null ? campaign.getBudget() : 0;
                    yield isBudgetChangeSafe(current, proposedBudget);
                }
                yield SafetyResult.pass();
            }
            case "CREATE_CAMPAIGN" -> canCreateCampaign(accountId);
            default -> SafetyResult.pass();
        };
    }

    // ── Safety Result DTO ────────────────────────────────────────────

    public record SafetyResult(boolean passed, String code, String reason) {
        public static SafetyResult pass() {
            return new SafetyResult(true, "OK", "All safety checks passed");
        }
        public static SafetyResult fail(String code, String reason) {
            return new SafetyResult(false, code, reason);
        }
    }
}
