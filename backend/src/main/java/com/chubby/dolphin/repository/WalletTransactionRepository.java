package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    List<WalletTransaction> findTop50ByAccountIdOrderByCreatedAtDesc(String accountId);

    boolean existsByReferenceId(String referenceId);

    default List<WalletTransaction> findTop50ByWorkspaceIdOrderByCreatedAtDesc(String workspaceId) {
        return findTop50ByAccountIdOrderByCreatedAtDesc(workspaceId);
    }
}
