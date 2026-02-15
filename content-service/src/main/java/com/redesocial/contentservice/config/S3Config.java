package com.redesocial.contentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
public class S3Config {
    
    @Value("${aws.s3.region}")
    private String region;
    
    @Value("${aws.s3.access-key:}")
    private String accessKey;
    
    @Value("${aws.s3.secret-key:}")
    private String secretKey;
    
    @Value("${aws.s3.endpoint:}")
    private String endpoint;
    
    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region));
        
        // Configure credentials if provided
        if (!accessKey.isEmpty() && !secretKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }
        
        // Configure custom endpoint (for LocalStack or MinIO)
        if (!endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        
        return builder.build();
    }
}
