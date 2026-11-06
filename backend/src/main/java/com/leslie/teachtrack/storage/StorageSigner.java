package com.leslie.teachtrack.storage;

import com.leslie.teachtrack.config.TeachTrackProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StorageSigner {
    private static final Logger log = LoggerFactory.getLogger(StorageSigner.class);
    private static final String ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public StorageSigner(TeachTrackProperties properties) {
        String configured = properties.storage() == null ? "" : properties.storage().secret();
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("TEACHTRACK_STORAGE_SECRET is empty; generated an ephemeral secret. "
                    + "Signed URLs will stop working after restart. Set it in production.");
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String sign(String action, String key, long expiresAtEpochSeconds) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            String payload = action + "\n" + key + "\n" + expiresAtEpochSeconds;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign storage URL", exception);
        }
    }

    public boolean verify(String action, String key, long expiresAtEpochSeconds, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        if (expiresAtEpochSeconds < System.currentTimeMillis() / 1000) {
            return false;
        }
        String expected = sign(action, key, expiresAtEpochSeconds);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
