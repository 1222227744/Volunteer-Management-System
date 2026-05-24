package com.volunteer.vms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "vms.cors")
public record CorsProperties(
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials
) {
    public String[] originPatterns() {
        return normalize(allowedOriginPatterns, List.of("http://localhost:5173", "http://127.0.0.1:5173"));
    }

    public String[] methods() {
        return normalize(allowedMethods, List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    }

    public String[] headers() {
        return normalize(allowedHeaders, List.of("Authorization", "Content-Type"));
    }

    private String[] normalize(List<String> values, List<String> fallback) {
        List<String> source = values == null || values.isEmpty() ? fallback : values;
        return source.toArray(String[]::new);
    }
}
