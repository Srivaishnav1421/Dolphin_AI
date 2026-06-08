package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Wallet;
import com.chubby.dolphin.entity.WalletTransaction;
import com.chubby.dolphin.repository.WalletRepository;
import com.chubby.dolphin.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional
    public Wallet getOrCreateWallet(String workspaceId) {
        return walletRepository.findFirstByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .workspaceId(workspaceId)
                            .balance(0.0)
                            .promoBalance(0.0)
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    @Transactional
    public double getBalance(String workspaceId) {
        return getOrCreateWallet(workspaceId).getBalance();
    }

    @Transactional
    public void creditWallet(String workspaceId, double amount, String txType, String refId, String refType) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive.");
        }
        
        // Locking wallet using pessimistic lock for thread safety (DA-042)
        Wallet wallet = walletRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .workspaceId(workspaceId)
                            .balance(0.0)
                            .promoBalance(0.0)
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return walletRepository.save(newWallet);
                });

        double balanceBefore = wallet.getBalance();
        double balanceAfter = balanceBefore + amount;

        wallet.setBalance(balanceAfter);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Record immutable ledger entry (DA-032)
        WalletTransaction tx = WalletTransaction.builder()
                .workspaceId(workspaceId)
                .wallet(wallet)
                .balanceBefore(balanceBefore)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .transactionType(txType)
                .referenceId(refId)
                .referenceType(refType)
                .createdAt(LocalDateTime.now())
                .build();
        walletTransactionRepository.save(tx);
        
        log.info("Wallet credited: workspace={}, amount={}, before={}, after={}", workspaceId, amount, balanceBefore, balanceAfter);
    }

    @Transactional
    public boolean debitWallet(String workspaceId, double amount, String txType, String refId, String refType) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive.");
        }

        // Locking wallet using pessimistic lock for thread safety (DA-042)
        Wallet wallet = walletRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new IllegalStateException("Wallet not initialized for workspace: " + workspaceId));

        double balanceBefore = wallet.getBalance();
        if (balanceBefore < amount) {
            log.warn("Insufficient wallet balance for workspace: {}. Available: {}, Required: {}", workspaceId, balanceBefore, amount);
            return false;
        }

        double balanceAfter = balanceBefore - amount;
        wallet.setBalance(balanceAfter);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Record immutable ledger entry (DA-032)
        WalletTransaction tx = WalletTransaction.builder()
                .workspaceId(workspaceId)
                .wallet(wallet)
                .balanceBefore(balanceBefore)
                .amount(-amount)
                .balanceAfter(balanceAfter)
                .transactionType(txType)
                .referenceId(refId)
                .referenceType(refType)
                .createdAt(LocalDateTime.now())
                .build();
        walletTransactionRepository.save(tx);

        log.info("Wallet debited: workspace={}, amount={}, before={}, after={}", workspaceId, amount, balanceBefore, balanceAfter);
        return true;
    }
}
