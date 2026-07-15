package com.alnlabs.ridebuddy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        boolean mockOtp,
        String mockOtpCode,
        String jwtSecret,
        long accessTokenMinutes,
        long refreshTokenDays
) {}
