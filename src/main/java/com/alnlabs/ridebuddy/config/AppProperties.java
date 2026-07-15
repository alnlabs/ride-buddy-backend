package com.alnlabs.ridebuddy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Share share
) {
    public record Cors(String allowedOrigins) {}
    public record Share(String rideBaseUrl) {}
}
