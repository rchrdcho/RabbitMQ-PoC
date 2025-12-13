package com.example.mqpoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "messaging")
public record MessagingProperties(
        Shared shared,
        Map<String, Service> services
) {

    public record Shared(
            String dlx,
            String dlq
    ) {
    }

    public record Service(
            String prefix,
            String exchange,
            String queue,
            String schemaVersion,
            String sourceService
    ) {
    }
}
