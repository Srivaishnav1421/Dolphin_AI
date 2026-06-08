package com.chubby.dolphin.brain.execution;

import com.chubby.dolphin.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BrainExecutionPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public BrainExecutionPublisher(@Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishExecution(String decisionId) {
        try {
            if (rabbitTemplate != null) {
                log.info("🔌 Dispatching execution payload to RabbitMQ queue: id={}", decisionId);
                rabbitTemplate.convertAndSend(RabbitConfig.EXECUTION_QUEUE, decisionId);
            } else {
                log.warn("⚠️ RabbitMQ not loaded. Execution will bypass publisher queue.");
            }
        } catch (Exception e) {
            log.error("Failed to enqueue execution tasks: {}", e.getMessage());
        }
    }
}
