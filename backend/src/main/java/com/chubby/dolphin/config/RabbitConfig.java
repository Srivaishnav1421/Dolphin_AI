package com.chubby.dolphin.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

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

    @Bean
    public MessageConverter rabbitMessageConverter() {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("com.chubby.dolphin.dto", "java.lang", "java.util");

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rabbitMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAfterReceivePostProcessors(message -> {
            Object typeId = message.getMessageProperties().getHeaders().get("__TypeId__");
            if (typeId != null && !List.of(
                    "com.chubby.dolphin.dto.BrainDecisionMessage",
                    "java.lang.String"
            ).contains(typeId.toString())) {
                throw new SecurityException("Rejected RabbitMQ message type: " + typeId);
            }
            return message;
        });
        return factory;
    }
}
