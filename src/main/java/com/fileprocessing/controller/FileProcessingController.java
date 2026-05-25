package com.fileprocessing.controller;

import com.fileprocessing.service.FileProcessingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for triggering file processing
 * Provides endpoints to process files from S3
 */
@RestController
@RequestMapping("/api/v1/files")
@Slf4j
@RequiredArgsConstructor
public class FileProcessingController {
    
    private final FileProcessingService fileProcessingService;
    
    /**
     * Process a file from S3
     * 
     * @param request Request containing S3 key
     * @return Processing result
     */
    @PostMapping("/process")
    public CompletableFuture<ResponseEntity<FileProcessingService.ProcessingResult>> processFile(
            @RequestBody ProcessFileRequest request) {
        
        log.info("Received request to process file: {}", request.getS3Key());
        
        return fileProcessingService.processFileFromS3(request.getS3Key())
            .thenApply(result -> {
                if (result.isSuccess()) {
                    return ResponseEntity.ok(result);
                } else {
                    return ResponseEntity.internalServerError().body(result);
                }
            })
            .exceptionally(throwable -> {
                log.error("Error processing file: {}", request.getS3Key(), throwable);
                return ResponseEntity.internalServerError().build();
            });
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "File processing service is running"));
    }
    
    @Data
    public static class ProcessFileRequest {
        private String s3Key;
    }
    
    @Data
    @lombok.AllArgsConstructor
    public static class HealthResponse {
        private String status;
        private String message;
    }
}

// Made with Bob
