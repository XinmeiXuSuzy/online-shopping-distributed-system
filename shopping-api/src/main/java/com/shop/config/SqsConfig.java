package com.shop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;
import java.time.Duration;

@Configuration
public class SqsConfig {

    @Value("${aws.region:us-east-1}")
    private String region;

    /**
     * Optional override endpoint for LocalStack in local/test environments.
     * In production (ECS), this is empty and the SDK uses the real AWS endpoint.
     */
    @Value("${aws.sqs.endpoint:}")
    private String sqsEndpoint;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        SqsAsyncClient.Builder builder = SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(30))
                        .maxConcurrency(50));

        if (sqsEndpoint != null && !sqsEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }

        return builder.build();
    }
}
