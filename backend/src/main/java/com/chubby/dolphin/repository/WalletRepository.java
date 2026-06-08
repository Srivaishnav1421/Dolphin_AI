package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Wallet> findByAccountId(String accountId);

    // Read-only query without lock
    Optional<Wallet> findFirstByAccountId(String accountId);

    default Optional<Wallet> findByWorkspaceId(String workspaceId) {
        return findByAccountId(workspaceId);
    }

    default Optional<Wallet> findFirstByWorkspaceId(String workspaceId) {
        return findFirstByAccountId(workspaceId);
    }
}
