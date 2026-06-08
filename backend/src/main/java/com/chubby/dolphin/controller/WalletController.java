package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.Wallet;
import com.chubby.dolphin.entity.WalletTransaction;
import com.chubby.dolphin.repository.WalletRepository;
import com.chubby.dolphin.repository.WalletTransactionRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.AlertService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletRepository            walletRepo;
    private final WalletTransactionRepository txRepo;
    private final SecurityUtils               sec;
    private final AlertService                alertService;

    public WalletController(WalletRepository walletRepo,
                            WalletTransactionRepository txRepo,
                            SecurityUtils sec,
                            AlertService alertService) {
        this.walletRepo = walletRepo;
        this.txRepo = txRepo;
        this.sec = sec;
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<Wallet> get() {
        return walletRepo.findFirstByWorkspaceId(sec.currentWorkspaceId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Manual fund (fallback without Razorpay) */
    @PostMapping("/fund")
    @Transactional
    public ResponseEntity<Wallet> fund(@RequestBody Map<String, Double> body) {
        String workspaceId = sec.currentWorkspaceId();
        double amount = body.getOrDefault("amount", 0.0);
        if (amount <= 0) return ResponseEntity.badRequest().build();

        Wallet wallet = getOrCreateWallet(workspaceId);
        wallet.setBalance(wallet.getBalance() + amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepo.save(wallet);

        // Record transaction
        recordTx(workspaceId, "CREDIT", amount, wallet.getBalance(), "Manual top-up", null);

        // Alert if this was triggered because balance was low
        if (wallet.getBalance() < 1000) {
            alertService.notifyLowBalance(wallet.getBalance());
        }
        return ResponseEntity.ok(wallet);
    }

    /** Update daily budget limit */
    @PutMapping("/limit")
    @Transactional
    public ResponseEntity<Wallet> updateLimit(@RequestBody Map<String, Double> body) {
        return walletRepo.findByWorkspaceId(sec.currentWorkspaceId()).map(w -> {
            w.setDailyBudgetLimit(body.getOrDefault("daily_budget_limit", w.getDailyBudgetLimit()));
            w.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(walletRepo.save(w));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Full transaction history — latest 50 */
    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransaction>> transactions() {
        String workspaceId = sec.currentWorkspaceId();
        return ResponseEntity.ok(txRepo.findTop50ByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
    }

    // ── Helpers ──────────────────────────────────────────────────
    private Wallet getOrCreateWallet(String workspaceId) {
        return walletRepo.findByWorkspaceId(workspaceId).orElseGet(() -> {
            Wallet w = new Wallet();
            w.setWorkspaceId(workspaceId);
            w.setBalance(0.0);
            w.setTotalSpent(0.0);
            w.setDailyBudgetLimit(10000.0);
            return w;
        });
    }

    public void recordTx(String workspaceId, String type, double amount,
                         double balanceAfter, String desc, String refId) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWorkspaceId(workspaceId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription(desc);
        tx.setReferenceId(refId);
        tx.setCreatedAt(LocalDateTime.now());
        txRepo.save(tx);
    }
}
