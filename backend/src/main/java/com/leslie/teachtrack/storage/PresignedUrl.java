package com.leslie.teachtrack.storage;

import java.time.Instant;

public record PresignedUrl(String url, String method, Instant expiresAt) {
}
