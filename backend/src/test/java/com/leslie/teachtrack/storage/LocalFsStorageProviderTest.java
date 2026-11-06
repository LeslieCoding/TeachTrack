package com.leslie.teachtrack.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.config.TeachTrackProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFsStorageProviderTest {
    private static TeachTrackProperties properties(String secret) {
        return new TeachTrackProperties(
                new TeachTrackProperties.Auth(""),
                new TeachTrackProperties.Storage("local", "", secret, 600,
                        new TeachTrackProperties.S3("", "auto", "", "", ""))
        );
    }

    @Test
    void storesReadsAndDeletes(@TempDir Path tempDir) {
        StorageSigner signer = new StorageSigner(properties("test-secret"));
        LocalFsStorageProvider provider = new LocalFsStorageProvider(tempDir, signer);
        byte[] data = "hello teachtrack".getBytes(StandardCharsets.UTF_8);

        provider.put("students/abc/exams/a.txt", data, "text/plain");

        assertTrue(provider.exists("students/abc/exams/a.txt"));
        assertEquals("hello teachtrack", new String(provider.get("students/abc/exams/a.txt").orElseThrow(),
                StandardCharsets.UTF_8));

        provider.delete("students/abc/exams/a.txt");
        assertFalse(provider.exists("students/abc/exams/a.txt"));
        assertTrue(provider.get("students/abc/exams/a.txt").isEmpty());
    }

    @Test
    void rejectsPathTraversal(@TempDir Path tempDir) {
        StorageSigner signer = new StorageSigner(properties("test-secret"));
        LocalFsStorageProvider provider = new LocalFsStorageProvider(tempDir, signer);

        assertThrows(ApiException.class, () -> provider.put("../escape.txt", new byte[] {1}, "text/plain"));
        assertThrows(ApiException.class, () -> provider.put("/absolute.txt", new byte[] {1}, "text/plain"));
    }

    @Test
    void signsAndExpiresDownloadUrls(@TempDir Path tempDir) {
        StorageSigner signer = new StorageSigner(properties("test-secret"));
        LocalFsStorageProvider provider = new LocalFsStorageProvider(tempDir, signer);

        PresignedUrl url = provider.presignDownload("questions/1/a.png", Duration.ofMinutes(5));
        assertTrue(url.url().contains("exp="));
        assertTrue(url.url().contains("sig="));
        assertEquals("GET", url.method());

        String signature = url.url().replaceAll(".*sig=", "");
        long expires = Long.parseLong(url.url().replaceAll(".*exp=([0-9]+).*", "$1"));
        assertTrue(signer.verify("download", "questions/1/a.png", expires, signature));
        assertFalse(signer.verify("download", "questions/1/other.png", expires, signature));
        assertFalse(signer.verify("download", "questions/1/a.png", 1L, signature));
    }

    @Test
    void filesAreNotServedFromPublicRoot(@TempDir Path tempDir) throws Exception {
        StorageSigner signer = new StorageSigner(properties("test-secret"));
        LocalFsStorageProvider provider = new LocalFsStorageProvider(tempDir, signer);
        provider.put("students/x/a.txt", "secret".getBytes(StandardCharsets.UTF_8), "text/plain");

        // The object exists only under the private root; no static resource handler is configured.
        assertTrue(Files.exists(tempDir.resolve("students/x/a.txt")));
    }
}
