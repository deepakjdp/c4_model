package com.fileprocessing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

import java.time.Duration;

/**
 * AWS S3 Configuration
 * Configures S3 clients with optimized settings for streaming large files
 */
@Configuration
@ConfigurationProperties(prefix = "aws")
@Data
public class AwsS3Config {
    
    private S3Properties s3;
    private CredentialsProperties credentials;
    
    @Data
    public static class S3Properties {
        private String region;
        private String bucket;
        private long chunkSize;
        private int maxConnections;
    }
    
    @Data
    public static class CredentialsProperties {
        private String accessKey;
        private String secretKey;
        private boolean useInstanceProfile;
    }
    
    /**
     * Creates AWS credentials provider based on configuration
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (credentials.isUseInstanceProfile() || 
            credentials.getAccessKey() == null || 
            credentials.getAccessKey().isEmpty()) {
            // Use default credentials provider chain (IAM role, environment variables, etc.)
            return DefaultCredentialsProvider.create();
        } else {
            // Use explicit credentials
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    credentials.getAccessKey(), 
                    credentials.getSecretKey()
                )
            );
        }
    }
    
    /**
     * Synchronous S3 Client with optimized connection pool
     * Used for metadata operations and small file operations
     */
    @Bean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
            .region(Region.of(s3.getRegion()))
            .credentialsProvider(credentialsProvider)
            .httpClientBuilder(ApacheHttpClient.builder()
                .maxConnections(s3.getMaxConnections())
                .connectionTimeout(Duration.ofSeconds(30))
                .socketTimeout(Duration.ofSeconds(60)))
            .build();
    }
    
    /**
     * Asynchronous S3 Client for streaming large files
     * This is memory-efficient and non-blocking
     */
    @Bean
    public S3AsyncClient s3AsyncClient(AwsCredentialsProvider credentialsProvider) {
        return S3AsyncClient.builder()
            .region(Region.of(s3.getRegion()))
            .credentialsProvider(credentialsProvider)
            .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                .maxConcurrency(s3.getMaxConnections())
                .connectionTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60)))
            .build();
    }
}

// Made with Bob
