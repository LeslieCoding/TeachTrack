package com.leslie.teachtrack.storage;

import java.time.Duration;
import java.util.Optional;

public interface ObjectStorageProvider {
    String providerId();

    void put(String key, byte[] data, String contentType);

    Optional<byte[]> get(String key);

    boolean exists(String key);

    void delete(String key);

    PresignedUrl presignDownload(String key, Duration ttl);

    PresignedUrl presignUpload(String key, String contentType, Duration ttl);
}
