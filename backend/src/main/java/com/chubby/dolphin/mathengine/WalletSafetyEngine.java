package com.chubby.dolphin.mathengine;

import com.chubby.dolphin.entity.Wallet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WalletSafetyEngine {

    public static final String EVALUATION_TYPE = "WALLET_SAFETY";
    public static final String FORMULA_VERSION = "wallet-safety-v1";

    @Value("${dolphin.math.wallet.low-threshold-inr:1000}")
    private double lowThreshold;

    @Value("${dolphin.math.wallet.critical-threshold-inr:0}")
    private double criticalThreshold;

    @Value("${dolphin.math.daily-spend-cap-inr:5000}")
    private double dailySpendCap;

    public MathSignal evaluate(Wallet wallet, Double projectedSpendToday) {
        MathInputSnapshot input = new MathInputSnapshot()
                .put("walletId", wallet != null ? wallet.getId() : null)
                .put("accountId", wallet != null ? wallet.getAccountId() : null)
                .put("balance", wallet != null ? wallet.getBalance() : null)
                .put("dailyBudgetLimit", wallet != null ? wallet.getDailyBudgetLimit() : null)
                .put("projectedSpendToday", projectedSpendToday)
                .put("lowThreshold", lowThreshold)
                .put("criticalThreshold", criticalThreshold)
                .put("dailySpendCap", dailySpendCap);

        if (wallet == null || wallet.getBalance() == null) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.NOT_ENOUGH_DATA, MathSeverity.INFO,
                    MathActionType.NONE, null, "Not enough wallet data",
                    "Wallet balance is required for wallet safety evaluation.",
                    input, FORMULA_VERSION, false);
        }

        double balance = wallet.getBalance();
        double cap = wallet.getDailyBudgetLimit() != null && wallet.getDailyBudgetLimit() > 0
                ? Math.min(wallet.getDailyBudgetLimit(), dailySpendCap)
                : dailySpendCap;

        if (balance <= criticalThreshold) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.CRITICAL,
                    MathActionType.PAUSE_ALL_REQUIRED, null, "Wallet depleted",
                    "Wallet balance is at or below the critical threshold. Future automation must require approval.",
                    input, FORMULA_VERSION, true);
        }
        if (projectedSpendToday != null && projectedSpendToday > cap) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                    MathActionType.CHANGE_BUDGET, null, "Daily spend cap breach",
                    "Projected spend today is above the configured daily cap.",
                    input.put("effectiveDailyCap", cap), FORMULA_VERSION, true);
        }
        if (balance <= lowThreshold) {
            return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.HIGH,
                    MathActionType.ALERT_LOW_WALLET, null, "Wallet balance low",
                    "Wallet balance is below the configured low-balance threshold.",
                    input, FORMULA_VERSION, false);
        }
        return MathSignal.of(EVALUATION_TYPE, MathEvaluationStatus.OK, MathSeverity.LOW,
                MathActionType.NONE, null, "Wallet safety normal",
                "Wallet balance and projected spend are within configured safety limits.",
                input, FORMULA_VERSION, false);
    }
}
