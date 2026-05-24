package com.volunteer.vms.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenSessionService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Long> revokedTokenIds = new ConcurrentHashMap<>();

    public TokenSessionService(UserRepository userRepository,
                               JwtProperties jwtProperties,
                               ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    public String createToken(User user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + Math.max(jwtProperties.ttlSeconds(), 300);
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        return sign(Map.of("alg", "HS256", "typ", "JWT"), Map.of(
                "iss", jwtProperties.issuer(),
                "sub", String.valueOf(user.getId()),
                "username", user.getUsername(),
                "role", user.getRole().name(),
                "iat", issuedAt,
                "exp", expiresAt,
                "jti", tokenId
        ));
    }

    public Optional<User> resolveUser(String token) {
        JwtPayload payload = verify(token).orElse(null);
        if (payload == null || revoked(payload.tokenId(), payload.expiresAt())) {
            return Optional.empty();
        }
        return userRepository.findById(payload.userId());
    }

    public void removeToken(String token) {
        verify(token).ifPresent(payload -> revokedTokenIds.put(payload.tokenId(), payload.expiresAt()));
        cleanupExpiredRevokedTokens();
    }

    private String sign(Map<String, Object> header, Map<String, Object> payload) {
        try {
            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encode(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            return signingInput + "." + encode(hmac(signingInput));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成JWT", ex);
        }
    }

    private Optional<JwtPayload> verify(String token) {
        try {
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] expected = hmac(signingInput);
            byte[] actual = decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            JsonNode payload = objectMapper.readTree(decode(parts[1]));
            if (!jwtProperties.issuer().equals(payload.path("iss").asText())) {
                return Optional.empty();
            }
            long expiresAt = payload.path("exp").asLong(0);
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            long userId = Long.parseLong(payload.path("sub").asText());
            String tokenId = payload.path("jti").asText();
            if (tokenId == null || tokenId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new JwtPayload(userId, tokenId, expiresAt));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private boolean revoked(String tokenId, long expiresAt) {
        Long revokedExpiresAt = revokedTokenIds.get(tokenId);
        return revokedExpiresAt != null && revokedExpiresAt >= expiresAt;
    }

    private void cleanupExpiredRevokedTokens() {
        long now = Instant.now().getEpochSecond();
        revokedTokenIds.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private byte[] hmac(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(resolveSecret(), HMAC_ALGORITHM));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] resolveSecret() {
        String secret = jwtProperties.secret();
        if (secret == null || secret.length() < 32) {
            secret = "dev-only-change-this-jwt-secret-at-least-32chars";
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private record JwtPayload(Long userId, String tokenId, long expiresAt) {
    }
}
