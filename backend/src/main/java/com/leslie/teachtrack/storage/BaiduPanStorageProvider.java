package com.leslie.teachtrack.storage;

import com.leslie.teachtrack.common.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Baidu Netdisk storage through the `bypy` CLI (official Baidu PCS API underneath).
 *
 * Baidu restricts third-party apps to /apps/{appname}/; bypy uses /apps/bypy.
 * Files are uploaded/downloaded by the backend, so Android never needs a Baidu token.
 * `get` is also used by the signed-download endpoint; the provider itself does not
 * expose Baidu's dlink.
 */
public class BaiduPanStorageProvider implements ObjectStorageProvider {
    private final String remoteRoot;
    private final String bypyCommand;
    private final int timeoutSeconds;
    private final StorageSigner signer;

    public BaiduPanStorageProvider(String remoteRoot, String bypyCommand, int timeoutSeconds, StorageSigner signer) {
        this.remoteRoot = normalizeRoot(remoteRoot);
        this.bypyCommand = bypyCommand;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
        this.signer = signer;
    }

    @Override
    public String providerId() {
        return "baidu-pan";
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        Path work = null;
        try {
            work = Files.createTempDirectory("teachtrack-baidu-");
            Path local = work.resolve(fileName(key));
            Files.write(local, data);
            ensureDirectories(key);
            run("upload", local.toString(), remotePath(key), "overwrite");
        } catch (IOException exception) {
            throw new IllegalStateException("百度网盘上传失败: " + key, exception);
        } finally {
            deleteQuietly(work);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path work = null;
        try {
            work = Files.createTempDirectory("teachtrack-baidu-");
            Path local = work.resolve(fileName(key));
            ProcessResult result = runAllowFailure("download", remotePath(key), local.toString());
            if (result.exitCode() != 0 || !Files.isRegularFile(local)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(local));
        } catch (IOException exception) {
            throw new IllegalStateException("百度网盘下载失败: " + key, exception);
        } finally {
            deleteQuietly(work);
        }
    }

    @Override
    public boolean exists(String key) {
        Path work = null;
        try {
            work = Files.createTempDirectory("teachtrack-baidu-");
            Path local = work.resolve(fileName(key));
            ProcessResult result = runAllowFailure("download", remotePath(key), local.toString());
            return result.exitCode() == 0 && Files.isRegularFile(local);
        } catch (IOException exception) {
            return false;
        } finally {
            deleteQuietly(work);
        }
    }

    @Override
    public void delete(String key) {
        runAllowFailure("delete", remotePath(key));
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
        long epoch = expiresAt.getEpochSecond();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        String path = action.equals("download")
                ? "/api/v1/assets/download/" + encoded
                : "/api/v1/assets/upload/" + encoded;
        return new PresignedUrl(
                path + "?exp=" + epoch + "&sig=" + signer.sign(action, key, epoch),
                action.equals("download") ? "GET" : "PUT",
                expiresAt);
    }

    private void ensureDirectories(String key) {
        String[] parts = key.split("/");
        StringBuilder current = new StringBuilder(remoteRoot);
        runAllowFailure("mkdir", current.toString());
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].isBlank()) {
                continue;
            }
            current.append('/').append(parts[i]);
            runAllowFailure("mkdir", current.toString());
        }
    }

    private ProcessResult run(String... args) {
        ProcessResult result = runAllowFailure(args);
        if (result.exitCode() != 0) {
            throw ApiException.badRequest("BAIDU_PAN_ERROR",
                    "bypy 命令失败(" + result.exitCode() + "): " + truncate(result.output()));
        }
        return result;
    }

    private ProcessResult runAllowFailure(String... args) {
        List<String> command = new ArrayList<>();
        if (bypyCommand.endsWith(".bat") || bypyCommand.endsWith(".cmd")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(bypyCommand);
        command.addAll(List.of(args));
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(-1, "timeout after " + timeoutSeconds + "s: " + output);
            }
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException exception) {
            return new ProcessResult(-1, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "interrupted");
        }
    }

    private String remotePath(String key) {
        return remoteRoot + "/" + key.replaceAll("^/+", "");
    }

    private static String normalizeRoot(String root) {
        String value = root == null || root.isBlank() ? "teachtrack" : root.trim();
        value = value.replace('\\', '/').replaceAll("/+$", "");
        if (value.startsWith("/apps/bypy/")) {
            value = value.substring("/apps/bypy/".length());
        } else if (value.startsWith("/apps/bypy")) {
            value = value.substring("/apps/bypy".length());
        }
        value = value.replaceAll("^/+", "");
        return value.isBlank() ? "teachtrack" : value;
    }

    private static String fileName(String key) {
        String value = key.replace('\\', '/');
        int index = value.lastIndexOf('/');
        String name = index >= 0 ? value.substring(index + 1) : value;
        return name.isBlank() ? "file" : name;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var files = Files.list(path)) {
                    files.forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException ignored) {
                            // best effort temp cleanup
                        }
                    });
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort temp cleanup
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
