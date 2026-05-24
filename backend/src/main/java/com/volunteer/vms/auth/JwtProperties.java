package com.volunteer.vms.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vms.auth.jwt")
public record JwtProperties(
        String secret,
        long ttlSeconds,
        String issuer
) {
}
