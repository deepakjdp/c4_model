package com.fileprocessing.service;

import com.fileprocessing.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for publishing messages to RabbitMQ
 * Supports batching to optimize performance and reduce memory usage
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RabbitMQPublisherService {
    
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    
    /**
     * Publish a single message to RabbitMQ
     * 
     * @param message Message to publish
     */
    public void publishMessage(Object message) {
        try {
            rabbitTemplate.convertAndSend(
                rabbitMQConfig.getExchange(),
                rabbitMQConfig.getRoutingKey(),
                message
            );
            log.debug("Published message to RabbitMQ: exchange={}, routingKey={}", 
                rabbitMQConfig.getExchange(), rabbitMQConfig.getRoutingKey());
        } catch (Exception e) {
            log.error("Failed to publish message to RabbitMQ", e);
            throw new RuntimeException("Failed to publish message to RabbitMQ", e);
        }
    }
    
    /**
     * Publish messages in batches to optimize performance
     * This is memory-efficient as it processes and sends messages in chunks
     * 
     * @param messages List of messages to publish
     */
    public void publishBatch(List<Object> messages) {
        if (messages == null || messages.isEmpty()) {
            log.debug("No messages to publish");
            return;
        }
        
        int batchSize = rabbitMQConfig.getBatchSize();
        int totalMessages = messages.size();
        int batchCount = (int) Math.ceil((double) totalMessages / batchSize);
        
        log.info("Publishing {} messages in {} batches of size {}", 
            totalMessages, batchCount, batchSize);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < totalMessages; i += batchSize) {
            int end = Math.min(i + batchSize, totalMessages);
            List<Object> batch = messages.subList(i, end);
            
            try {
                for (Object message : batch) {
                    rabbitTemplate.convertAndSend(
                        rabbitMQConfig.getExchange(),
                        rabbitMQConfig.getRoutingKey(),
                        message
                    );
                    successCount.incrementAndGet();
                }
                
                log.debug("Published batch {}/{}: {} messages", 
                    (i / batchSize) + 1, batchCount, batch.size());
                
            } catch (Exception e) {
                failureCount.addAndGet(batch.size());
                log.error("Failed to publish batch {}/{}", (i / batchSize) + 1, batchCount, e);
            }
        }
        
        log.info("Batch publishing completed: success={}, failures={}", 
            successCount.get(), failureCount.get());
    }
    
    /**
     * Batch message accumulator for streaming scenarios
     * Automatically publishes when batch size is reached
     */
    public static class BatchAccumulator {
        private final List<Object> batch;
        private final int batchSize;
        private final RabbitMQPublisherService publisherService;
        private final AtomicInteger totalPublished = new AtomicInteger(0);
        
        public BatchAccumulator(RabbitMQPublisherService publisherService, int batchSize) {
            this.publisherService = publisherService;
            this.batchSize = batchSize;
            this.batch = new ArrayList<>(batchSize);
        }
        
        /**
         * Add message to batch and publish if batch size is reached
         */
        public synchronized void add(Object message) {
            batch.add(message);
            
            if (batch.size() >= batchSize) {
                flush();
            }
        }
        
        /**
         * Publish remaining messages in batch
         */
        public synchronized void flush() {
            if (!batch.isEmpty()) {
                publisherService.publishBatch(new ArrayList<>(batch));
                totalPublished.addAndGet(batch.size());
                batch.clear();
            }
        }
        
        /**
         * Get total number of messages published
         */
        public int getTotalPublished() {
            return totalPublished.get();
        }
    }
    
    /**
     * Create a batch accumulator for streaming scenarios
     * 
     * @return BatchAccumulator instance
     */
    public BatchAccumulator createBatchAccumulator() {
        return new BatchAccumulator(this, rabbitMQConfig.getBatchSize());
    }
    
    /**
     * Publish a message with custom properties
     * 
     * @param message Message to publish
     * @param properties Custom message properties
     */
    public void publishMessageWithProperties(Object message, Map<String, Object> properties) {
        try {
            rabbitTemplate.convertAndSend(
                rabbitMQConfig.getExchange(),
                rabbitMQConfig.getRoutingKey(),
                message,
                msg -> {
                    properties.forEach((key, value) -> 
                        msg.getMessageProperties().setHeader(key, value));
                    return msg;
                }
            );
            log.debug("Published message with properties to RabbitMQ");
        } catch (Exception e) {
            log.error("Failed to publish message with properties to RabbitMQ", e);
            throw new RuntimeException("Failed to publish message with properties", e);
        }
    }
}

// Made with Bob
