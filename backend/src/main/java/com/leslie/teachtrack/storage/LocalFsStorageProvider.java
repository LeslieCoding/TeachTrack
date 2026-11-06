package com.leslie.teachtrack.storage;

import com.leslie.teachtrack.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Development / single-node storage. Files stay under a private root directory and
 * are only reachable through HMAC-signed, expiring URLs.
 */
public class LocalFsStorageProvider implements ObjectStorageProvider {
    private final Path root;
    private final StorageSigner signer;

    public LocalFsStorageProvider(Path root, StorageSigner signer) {
        this.root = root.toAbsolutePath().normalize();
        this.signer = signer;
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create storage root: " + this.root, exception);
        }
    }

    @Override
    public String providerId() {
        return "local";
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, data);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write storage object " + key, exception);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read storage object " + key, exception);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete storage object " + key, exception);
        }
    }

    @Override
    public PresignedUrl presignDownload(String key, Duration ttl) {
        return sign("download", key, ttl);
    }

    @Override
    public PresignedUrl presignUpload(String key, String contentType, Duration ttl) {
        return sign("upload", key, ttl);
    }

    private PresignedUrl sign(String action, String key, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        long epochSeconds = expiresAt.getEpochSecond();
        String encodedKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = signer.sign(action, key, epochSeconds);
        String path = action.equals("download")
                ? "/api/v1/assets/download/" + encodedKey
                : "/api/v1/assets/upload/" + encodedKey;
        String url = path + "?exp=" + epochSeconds + "&sig=" + signature;
        return new PresignedUrl(url, action.equals("download") ? "GET" : "PUT", expiresAt);
    }

    public StorageSigner signer() {
        return signer;
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("INVALID_STORAGE_KEY", "storage key is empty");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw ApiException.badRequest("INVALID_STORAGE_KEY", "storage key escapes the storage root");
        }
        return resolved;
    }
}
