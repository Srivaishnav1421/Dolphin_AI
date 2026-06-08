package com.chubby.dolphin.service;

import com.chubby.dolphin.entity.FinancialEvent;
import com.chubby.dolphin.repository.FinancialEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialEventService {

    private final FinancialEventRepository financialEventRepository;

    @Transactional
    public void recordFinancialEvent(String workspaceId, String eventType, double amount, String referenceId) {
        FinancialEvent event = FinancialEvent.builder()
                .workspaceId(workspaceId)
                .eventType(eventType)
                .amount(amount)
                .currency("INR")
                .referenceId(referenceId)
                .createdAt(LocalDateTime.now())
                .build();
        financialEventRepository.save(event);
        log.info("Financial event recorded: workspace={}, type={}, amount={}, ref={}", 
                workspaceId, eventType, amount, referenceId);
    }

    @Transactional(readOnly = true)
    public List<FinancialEvent> getWorkspaceFinancialEvents(String workspaceId) {
        return financialEventRepository.findByWorkspaceId(workspaceId);
    }
}
