package com.leslie.teachtrack.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public final class Ids {
    public static final UUID NAMESPACE = UUID.fromString("6f1c2f8a-4c1e-5b7a-9d3e-7a2b1c9d0e4f");

    private Ids() {
    }

    /** UUIDv5 (SHA-1, RFC 4122) so legacy text ids map deterministically. */
    public static UUID v5(String kind, String legacyId) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(uuidBytes(NAMESPACE));
            sha1.update((kind + ":" + legacyId).getBytes(StandardCharsets.UTF_8));
            byte[] hash = sha1.digest();
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb = (msb << 8) | (hash[i] & 0xff);
            }
            for (int i = 8; i < 16; i++) {
                lsb = (lsb << 8) | (hash[i] & 0xff);
            }
            return new UUID(msb, lsb);
        } catch (Exception exception) {
            throw new IllegalStateException("UUIDv5 is unavailable", exception);
        }
    }

    /** Keeps an existing UUID string; otherwise derives a stable UUID from the legacy id. */
    public static UUID fromLegacy(String kind, String legacyId) {
        if (legacyId == null || legacyId.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(legacyId);
        } catch (IllegalArgumentException ignored) {
            return v5(kind, legacyId);
        }
    }

    public static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] uuidBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
        }
        for (int i = 0; i < 8; i++) {
            bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return bytes;
    }
}
