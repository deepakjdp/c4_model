package com.fileprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main Spring Boot Application
 * Processes large files from S3 and publishes to RabbitMQ without memory issues
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
public class S3FileProcessorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(S3FileProcessorApplication.class, args);
    }
}

// Made with Bob
