package com.chubby.dolphin.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String OPTIMIZATION_QUEUE = "chubby.dolphin.brain.optimization";
    public static final String EXECUTION_QUEUE = "chubby.dolphin.brain.execution";

    @Bean
    public Queue optimizationQueue() {
        return new Queue(OPTIMIZATION_QUEUE, true);
    }

    @Bean
    public Queue executionQueue() {
        return new Queue(EXECUTION_QUEUE, true);
    }
}
