package com.leslie.teachtrack.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ApiTokenRepository repository;

    public TokenService(ApiTokenRepository repository) {
        this.repository = repository;
    }

    public String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "tt_" + URL_ENCODER.encodeToString(bytes);
    }

    public byte[] hash(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String createToken(UUID userId, String name, List<String> scopes) {
        String raw = generateRawToken();
        String prefix = raw.substring(0, Math.min(11, raw.length()));
        repository.insertToken(UUID.randomUUID(), userId, name, prefix, hash(raw), scopes);
        return raw;
    }

    /**
     * Creates exactly one owner token when the token table is empty.
     * The raw token is never logged; only its prefix is written.
     */
    public boolean bootstrapOwner(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || repository.count() > 0) {
            return false;
        }
        UUID userId = UUID.randomUUID();
        repository.insertUser(userId, "Owner");
        repository.insertRole(userId, "OWNER");
        String raw = rawToken.trim();
        String prefix = raw.substring(0, Math.min(11, raw.length()));
        repository.insertToken(UUID.randomUUID(), userId, "bootstrap", prefix, hash(raw),
                List.of("owner", "ai.read", "ai.write.draft"));
        return true;
    }
}
