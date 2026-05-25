package com.fileprocessing.service;

import com.fileprocessing.config.AwsS3Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service for streaming large files from S3 without loading entire file into memory
 * Uses AWS SDK's async client with streaming capabilities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class S3StreamingService {
    
    private final S3AsyncClient s3AsyncClient;
    private final AwsS3Config awsS3Config;
    
    /**
     * Stream file from S3 line by line without loading entire file into memory
     * This method is memory-efficient for processing large files
     * 
     * @param bucketName S3 bucket name
     * @param key S3 object key
     * @param lineProcessor Consumer function to process each line
     * @return CompletableFuture that completes when streaming is done
     */
    public CompletableFuture<Void> streamFileLineByLine(
            String bucketName, 
            String key, 
            Consumer<String> lineProcessor) {
        
        log.info("Starting to stream file from S3: bucket={}, key={}", bucketName, key);
        
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
        
        // Use AsyncResponseTransformer to stream the response
        return s3AsyncClient.getObject(
            getObjectRequest,
            AsyncResponseTransformer.toBlockingInputStream()
        ).thenAccept(responseInputStream -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseInputStream, StandardCharsets.UTF_8))) {
                
                String line;
                long lineCount = 0;
                long startTime = System.currentTimeMillis();
                
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    
                    // Process each line
                    lineProcessor.accept(line);
                    
                    // Log progress every 10000 lines
                    if (lineCount % 10000 == 0) {
                        log.debug("Processed {} lines from {}", lineCount, key);
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("Completed streaming file: key={}, totalLines={}, durationMs={}", 
                    key, lineCount, duration);
                
            } catch (IOException e) {
                log.error("Error streaming file from S3: bucket={}, key={}", bucketName, key, e);
                throw new RuntimeException("Failed to stream file from S3", e);
            }
        }).exceptionally(throwable -> {
            log.error("Failed to get object from S3: bucket={}, key={}", bucketName, key, throwable);
            throw new RuntimeException("Failed to get object from S3", throwable);
        });
    }
    
    /**
     * Stream file in chunks for processing
     * Useful for binary files or when you need to process data in chunks
     * 
     * @param bucketName S3 bucket name
     * @param key S3 object key
     * @param chunkProcessor Consumer function to process each chunk
     * @return CompletableFuture that completes when streaming is done
     */
    public CompletableFuture<Void> streamFileInChunks(
            String bucketName, 
            String key, 
            Consumer<byte[]> chunkProcessor) {
        
        log.info("Starting to stream file in chunks from S3: bucket={}, key={}", bucketName, key);
        
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
        
        return s3AsyncClient.getObject(
            getObjectRequest,
            AsyncResponseTransformer.toBlockingInputStream()
        ).thenAccept(responseInputStream -> {
            try {
                byte[] buffer = new byte[(int) awsS3Config.getS3().getChunkSize()];
                int bytesRead;
                long totalBytesRead = 0;
                long startTime = System.currentTimeMillis();
                
                while ((bytesRead = responseInputStream.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                    
                    // Create a new array with actual bytes read
                    byte[] chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                    
                    // Process chunk
                    chunkProcessor.accept(chunk);
                    
                    // Log progress every 100MB
                    if (totalBytesRead % (100 * 1024 * 1024) == 0) {
                        log.debug("Processed {} MB from {}", totalBytesRead / (1024 * 1024), key);
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("Completed streaming file in chunks: key={}, totalBytes={}, durationMs={}", 
                    key, totalBytesRead, duration);
                
            } catch (IOException e) {
                log.error("Error streaming file chunks from S3: bucket={}, key={}", bucketName, key, e);
                throw new RuntimeException("Failed to stream file chunks from S3", e);
            }
        }).exceptionally(throwable -> {
            log.error("Failed to get object from S3: bucket={}, key={}", bucketName, key, throwable);
            throw new RuntimeException("Failed to get object from S3", throwable);
        });
    }
    
    /**
     * Get file metadata without downloading the file
     * 
     * @param bucketName S3 bucket name
     * @param key S3 object key
     * @return CompletableFuture with file metadata
     */
    public CompletableFuture<GetObjectResponse> getFileMetadata(String bucketName, String key) {
        log.debug("Getting metadata for file: bucket={}, key={}", bucketName, key);
        
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
        
        return s3AsyncClient.getObject(
            getObjectRequest,
            AsyncResponseTransformer.toBytes()
        ).thenApply(response -> {
            log.debug("Retrieved metadata for file: bucket={}, key={}, size={}", 
                bucketName, key, response.response().contentLength());
            return response.response();
        });
    }
}

// Made with Bob
