package com.chubby.dolphin.service;

import com.chubby.dolphin.config.RabbitConfig;
import com.chubby.dolphin.dto.BrainDecisionMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BrainDecisionWorker {

    private final BrainDecisionService decisionService;

    public BrainDecisionWorker(BrainDecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @RabbitListener(queues = RabbitConfig.OPTIMIZATION_QUEUE)
    public void receiveMessage(BrainDecisionMessage msg) {
        log.info("📥 [RabbitMQ Worker] Received campaign optimization task for decisionId: {}", msg.getDecisionId());
        try {
            decisionService.executeDecisionAsync(msg.getDecisionId());
        } catch (Exception e) {
            log.error("[RabbitMQ Worker] Failed to execute optimization on Meta API: {}", e.getMessage(), e);
        }
    }
}
