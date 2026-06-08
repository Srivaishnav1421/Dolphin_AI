package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id")
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Column(name = "balance_before")
    private double balanceBefore;

    @Column(nullable = false)
    private double amount;

    @Column(name = "balance_after")
    private double balanceAfter;

    @Column(name = "type")
    private String type; // TOPUP, DEDUCTION, PROMO_EXPIRE, REFUND etc.

    @Column(name = "description")
    private String description;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getWorkspaceId() {
        return accountId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.accountId = workspaceId;
    }

    public String getTransactionType() {
        return type;
    }

    public void setTransactionType(String transactionType) {
        this.type = transactionType;
    }

    public static class WalletTransactionBuilder {
        public WalletTransactionBuilder workspaceId(String workspaceId) {
            this.accountId = workspaceId;
            return this;
        }

        public WalletTransactionBuilder transactionType(String transactionType) {
            this.type = transactionType;
            return this;
        }
    }
}
