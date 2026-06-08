package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", unique = true)
    private String accountId;

    @Column(name = "balance")
    @Builder.Default
    private Double balance = 0.00;

    @Column(name = "promo_balance")
    @Builder.Default
    private Double promoBalance = 0.00;

    @Column(name = "total_spent")
    @Builder.Default
    private Double totalSpent = 0.00;

    @Column(name = "daily_budget_limit")
    @Builder.Default
    private Double dailyBudgetLimit = 10000.00;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;

    public String getWorkspaceId() {
        return accountId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.accountId = workspaceId;
    }

    public static class WalletBuilder {
        public WalletBuilder workspaceId(String workspaceId) {
            this.accountId = workspaceId;
            return this;
        }
    }
}
