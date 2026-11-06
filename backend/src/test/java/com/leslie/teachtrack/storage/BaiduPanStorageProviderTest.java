package com.leslie.teachtrack.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leslie.teachtrack.config.TeachTrackProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaiduPanStorageProviderTest {
    private static TeachTrackProperties properties() {
        return new TeachTrackProperties(
                new TeachTrackProperties.Auth(""),
                new TeachTrackProperties.Storage("baidu-pan", "", "test-secret", 600,
                        new TeachTrackProperties.S3("", "auto", "", "", ""))
        );
    }

    @Test
    void uploadsAndDownloadsThroughByPyCommand(@TempDir Path tempDir) throws Exception {
        Path stub = tempDir.resolve("stub.py");
        Files.writeString(stub, """
                import os, shutil, sys
                base = os.path.dirname(os.path.abspath(__file__))
                root = os.path.join(base, "remote")
                prefix = "teachtrack/"
                def local(remote):
                    if remote.startswith(prefix):
                        return os.path.join(root, remote[len(prefix):])
                    return os.path.join(root, remote.lstrip("/"))
                cmd, args = sys.argv[1], sys.argv[2:]
                if cmd == "mkdir":
                    os.makedirs(local(args[0]), exist_ok=True)
                elif cmd == "upload":
                    target = local(args[1]); os.makedirs(os.path.dirname(target), exist_ok=True)
                    shutil.copy(args[0], target)
                elif cmd == "download":
                    shutil.copy(local(args[0]), args[1])
                elif cmd == "delete":
                    try: os.remove(local(args[0]))
                    except FileNotFoundError: pass
                """);
        Path bat = tempDir.resolve("stub.bat");
        Files.writeString(bat, "@echo off\r\npython \"%~dp0stub.py\" %*\r\n");

        StorageSigner signer = new StorageSigner(properties());
        BaiduPanStorageProvider provider = new BaiduPanStorageProvider(
                "/apps/bypy/teachtrack", bat.toString(), 30, signer);

        byte[] data = "百度网盘内容".getBytes(StandardCharsets.UTF_8);
        provider.put("students/s1/exam.txt", data, "text/plain");

        assertTrue(provider.exists("students/s1/exam.txt"));
        assertArrayEquals(data, provider.get("students/s1/exam.txt").orElseThrow());
        assertTrue(Files.isRegularFile(tempDir.resolve("remote/students/s1/exam.txt")));

        PresignedUrl url = provider.presignDownload("students/s1/exam.txt", Duration.ofMinutes(5));
        assertTrue(url.url().contains("/api/v1/assets/download/"));
        assertTrue(url.url().contains("sig="));

        provider.delete("students/s1/exam.txt");
        assertFalse(provider.exists("students/s1/exam.txt"));
    }
}
