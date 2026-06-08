package com.chubby.dolphin.brain.execution;

import com.chubby.dolphin.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BrainExecutionConsumer {

    private final BrainExecutionService executionService;

    @Autowired
    public BrainExecutionConsumer(BrainExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(queues = RabbitConfig.EXECUTION_QUEUE)
    public void consumeExecution(String decisionId) {
        log.info("📥 Consumed execution task from RabbitMQ: id={}", decisionId);
        try {
            ExecutionResult result = executionService.executeRecommendation(decisionId);
            log.info("✅ Finished background execution consumed run: id={}, status={}", 
                    decisionId, result.getStatus());
        } catch (Exception e) {
            log.error("Fatal exception during consumption run: {}", e.getMessage(), e);
        }
    }
}
