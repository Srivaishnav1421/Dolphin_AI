package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.Wallet;
import com.chubby.dolphin.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletRechargeService {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final FinancialEventService financialEventService;

    private static final double RECHARGE_THRESHOLD = 500.00;
    private static final double RECHARGE_AMOUNT = 2000.00;

    @Transactional
    public void checkAndAutoRecharge(String workspaceId) {
        log.info("Auto-recharge check requested for workspace {}, but auto-recharge is disabled due to regulatory compliance (RBI e-mandate friction). Notifications should be used instead.", workspaceId);
    }
}
