package com.chubby.dolphin.contentfactory;

import com.chubby.dolphin.approval.ApprovalSourceModule;
import com.chubby.dolphin.approval.ApprovalStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ContentFactoryApprovalListener {

    private final ContentFactoryService contentFactoryService;

    @EventListener
    public void onApprovalStatusChanged(ApprovalStatusChangedEvent event) {
        if (event.sourceModule() != ApprovalSourceModule.CONTENT_FACTORY) {
            return;
        }
        if (!"ContentFactoryVariant".equals(event.sourceEntityType())) {
            return;
        }
        try {
            contentFactoryService.markApprovalDecision(UUID.fromString(event.approvalItemId()), event.status());
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed external events; scoped queue reads still protect real workflows.
        }
    }
}
