package com.fileprocessing.service;

import com.fileprocessing.config.AwsS3Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main service that orchestrates file processing from S3 to RabbitMQ
 * Handles large files efficiently without loading entire file into memory
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileProcessingService {
    
    private final S3StreamingService s3StreamingService;
    private final RabbitMQPublisherService rabbitMQPublisherService;
    private final AwsS3Config awsS3Config;
    
    /**
     * Process a file from S3 and publish each line to RabbitMQ
     * This method streams the file line by line, ensuring memory efficiency
     * 
     * @param s3Key S3 object key
     * @return CompletableFuture that completes when processing is done
     */
    public CompletableFuture<ProcessingResult> processFileFromS3(String s3Key) {
        String bucketName = awsS3Config.getS3().getBucket();
        
        log.info("Starting file processing: bucket={}, key={}", bucketName, s3Key);
        
        // Create batch accumulator for efficient message publishing
        RabbitMQPublisherService.BatchAccumulator batchAccumulator = 
            rabbitMQPublisherService.createBatchAccumulator();
        
        long startTime = System.currentTimeMillis();
        
        return s3StreamingService.streamFileLineByLine(bucketName, s3Key, line -> {
            // Process each line and add to batch
            // You can transform the line here before publishing
            Map<String, Object> message = createMessage(s3Key, line);
            batchAccumulator.add(message);
            
        }).thenApply(v -> {
            // Flush remaining messages in batch
            batchAccumulator.flush();
            
            long duration = System.currentTimeMillis() - startTime;
            int totalMessages = batchAccumulator.getTotalPublished();
            
            ProcessingResult result = ProcessingResult.builder()
                .s3Key(s3Key)
                .totalMessagesPublished(totalMessages)
                .processingTimeMs(duration)
                .success(true)
                .build();
            
            log.info("File processing completed: key={}, messages={}, durationMs={}", 
                s3Key, totalMessages, duration);
            
            return result;
            
        }).exceptionally(throwable -> {
            log.error("File processing failed: key={}", s3Key, throwable);
            
            return ProcessingResult.builder()
                .s3Key(s3Key)
                .success(false)
                .errorMessage(throwable.getMessage())
                .build();
        });
    }
    
    /**
     * Process a file from S3 with custom transformation logic
     * 
     * @param s3Key S3 object key
     * @param lineTransformer Custom function to transform each line
     * @return CompletableFuture that completes when processing is done
     */
    public CompletableFuture<ProcessingResult> processFileWithTransformation(
            String s3Key, 
            LineTransformer lineTransformer) {
        
        String bucketName = awsS3Config.getS3().getBucket();
        
        log.info("Starting file processing with transformation: bucket={}, key={}", bucketName, s3Key);
        
        RabbitMQPublisherService.BatchAccumulator batchAccumulator = 
            rabbitMQPublisherService.createBatchAccumulator();
        
        long startTime = System.currentTimeMillis();
        
        return s3StreamingService.streamFileLineByLine(bucketName, s3Key, line -> {
            try {
                // Apply custom transformation
                Object transformedMessage = lineTransformer.transform(line);
                
                if (transformedMessage != null) {
                    batchAccumulator.add(transformedMessage);
                }
            } catch (Exception e) {
                log.error("Error transforming line from file: key={}", s3Key, e);
                // Continue processing other lines
            }
            
        }).thenApply(v -> {
            batchAccumulator.flush();
            
            long duration = System.currentTimeMillis() - startTime;
            int totalMessages = batchAccumulator.getTotalPublished();
            
            ProcessingResult result = ProcessingResult.builder()
                .s3Key(s3Key)
                .totalMessagesPublished(totalMessages)
                .processingTimeMs(duration)
                .success(true)
                .build();
            
            log.info("File processing with transformation completed: key={}, messages={}, durationMs={}", 
                s3Key, totalMessages, duration);
            
            return result;
            
        }).exceptionally(throwable -> {
            log.error("File processing with transformation failed: key={}", s3Key, throwable);
            
            return ProcessingResult.builder()
                .s3Key(s3Key)
                .success(false)
                .errorMessage(throwable.getMessage())
                .build();
        });
    }
    
    /**
     * Create a message object from a line
     * Override this method to customize message structure
     */
    protected Map<String, Object> createMessage(String s3Key, String line) {
        Map<String, Object> message = new HashMap<>();
        message.put("sourceFile", s3Key);
        message.put("content", line);
        message.put("timestamp", System.currentTimeMillis());
        return message;
    }
    
    /**
     * Functional interface for custom line transformation
     */
    @FunctionalInterface
    public interface LineTransformer {
        Object transform(String line) throws Exception;
    }
    
    /**
     * Result of file processing operation
     */
    @lombok.Data
    @lombok.Builder
    public static class ProcessingResult {
        private String s3Key;
        private int totalMessagesPublished;
        private long processingTimeMs;
        private boolean success;
        private String errorMessage;
    }
}

// Made with Bob
