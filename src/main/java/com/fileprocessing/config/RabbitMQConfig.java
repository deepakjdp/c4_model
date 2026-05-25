package com.fileprocessing.config;

import lombok.Data;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration
 * Sets up exchanges, queues, and bindings with dead letter queue support
 */
@Configuration
@ConfigurationProperties(prefix = "file.processing")
@Data
public class RabbitMQConfig {
    
    private String exchange;
    private String routingKey;
    private String queue;
    private int batchSize;
    private DlqProperties dlq;
    
    @Data
    public static class DlqProperties {
        private boolean enabled;
        private String exchange;
        private String queue;
    }
    
    /**
     * Main exchange for processed file messages
     */
    @Bean
    public DirectExchange fileProcessingExchange() {
        return new DirectExchange(exchange, true, false);
    }
    
    /**
     * Main queue for processed files
     * Configured with dead letter exchange for failed messages
     */
    @Bean
    public Queue processedFilesQueue() {
        if (dlq.isEnabled()) {
            return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlq.getExchange())
                .withArgument("x-dead-letter-routing-key", "dlq")
                .build();
        }
        return new Queue(queue, true);
    }
    
    /**
     * Binding between exchange and queue
     */
    @Bean
    public Binding processedFilesBinding(Queue processedFilesQueue, DirectExchange fileProcessingExchange) {
        return BindingBuilder
            .bind(processedFilesQueue)
            .to(fileProcessingExchange)
            .with(routingKey);
    }
    
    /**
     * Dead Letter Exchange for failed messages
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        if (dlq.isEnabled()) {
            return new DirectExchange(dlq.getExchange(), true, false);
        }
        return null;
    }
    
    /**
     * Dead Letter Queue
     */
    @Bean
    public Queue deadLetterQueue() {
        if (dlq.isEnabled()) {
            return new Queue(dlq.getQueue(), true);
        }
        return null;
    }
    
    /**
     * Binding for dead letter queue
     */
    @Bean
    public Binding deadLetterBinding() {
        if (dlq.isEnabled()) {
            return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dlq");
        }
        return null;
    }
    
    /**
     * JSON message converter for RabbitMQ
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, 
                                        MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // Enable publisher confirms for reliability
        template.setMandatory(true);
        return template;
    }
}

// Made with Bob
