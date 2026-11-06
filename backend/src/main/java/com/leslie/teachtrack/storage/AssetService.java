package com.leslie.teachtrack.storage;

import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AssetService {
    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;

    public record UploadTicket(UUID assetId, PresignedUrl presigned) {
    }

    private final AssetRepository repository;
    private final ObjectStorageProvider storage;
    private final StorageSigner signer;
    private final AuditService audit;
    private final Duration downloadTtl;

    public AssetService(
            AssetRepository repository,
            ObjectStorageProvider storage,
            StorageSigner signer,
            AuditService audit,
            com.leslie.teachtrack.config.TeachTrackProperties properties
    ) {
        this.repository = repository;
        this.storage = storage;
        this.signer = signer;
        this.audit = audit;
        int ttl = properties.storage() == null ? 600 : properties.storage().downloadTtlSeconds();
        this.downloadTtl = Duration.ofSeconds(ttl <= 0 ? 600 : ttl);
    }

    public UploadTicket createUpload(
            String ownerType,
            UUID ownerId,
            String filename,
            String contentType,
            long sizeBytes
    ) {
        if (sizeBytes > MAX_UPLOAD_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "最大允许 50 MB");
        }
        String safeOwner = ownerType == null || ownerType.isBlank() ? "general" : ownerType.trim().toLowerCase(Locale.ROOT);
        String safeName = sanitize(filename);
        String ownerSegment = ownerId == null ? "unassigned" : ownerId.toString();
        String key = safeOwner + "/" + ownerSegment + "/" + UUID.randomUUID() + "-" + safeName;
        String mime = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        UUID assetId = UUID.randomUUID();
        repository.insert(new AssetRepository.AssetRow(
                assetId, safeOwner, ownerId, storage.providerId(), key, mime, 0L, "", "PENDING"));
        PresignedUrl presigned = storage.presignUpload(key, mime, Duration.ofMinutes(10));
        audit.record(null, "asset.createUpload", "Asset", assetId, "SUCCESS", null);
        return new UploadTicket(assetId, presigned);
    }

    public void completeUpload(String encodedKey, long expiresAt, String signature, byte[] data) {
        String key = decodeKey(encodedKey);
        if (!signer.verify("upload", key, expiresAt, signature)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "INVALID_SIGNATURE", "上传链接无效或已过期");
        }
        AssetRepository.AssetRow asset = repository.findByKey(key)
                .orElseThrow(() -> ApiException.notFound("资源记录不存在"));
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "上传内容为空");
        }
        if (data.length > MAX_UPLOAD_BYTES) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "最大允许 50 MB");
        }
        try {
            storage.put(key, data, asset.mimeType());
            String sha256 = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(data));
            repository.markReady(asset.id(), data.length, sha256);
            audit.record(null, "asset.upload", "Asset", asset.id(), "SUCCESS", null);
        } catch (RuntimeException exception) {
            repository.markFailed(asset.id());
            audit.record(null, "asset.upload", "Asset", asset.id(), "FAILURE", "STORAGE_ERROR");
            throw exception;
        } catch (Exception exception) {
            repository.markFailed(asset.id());
            throw new IllegalStateException("Unable to hash upload", exception);
        }
    }

    public PresignedUrl downloadUrl(UUID assetId) {
        AssetRepository.AssetRow asset = repository.findById(assetId)
                .orElseThrow(() -> ApiException.notFound("资源不存在"));
        if (!"READY".equals(asset.status())) {
            throw ApiException.badRequest("ASSET_NOT_READY", "资源尚未上传完成");
        }
        return storage.presignDownload(asset.storageKey(), downloadTtl);
    }

    public DownloadedAsset download(String encodedKey, long expiresAt, String signature) {
        String key = decodeKey(encodedKey);
        if (!signer.verify("download", key, expiresAt, signature)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "INVALID_SIGNATURE", "下载链接无效或已过期");
        }
        AssetRepository.AssetRow asset = repository.findByKey(key)
                .orElseThrow(() -> ApiException.notFound("资源记录不存在"));
        byte[] bytes = storage.get(key)
                .orElseThrow(() -> ApiException.notFound("资源文件不存在"));
        audit.record(null, "asset.download", "Asset", asset.id(), "SUCCESS", null);
        return new DownloadedAsset(asset.mimeType(), bytes);
    }

    public void recordRead(AuthPrincipal principal, UUID assetId) {
        audit.record(principal, "asset.downloadUrl", "Asset", assetId, "SUCCESS", null);
    }

    public record DownloadedAsset(String contentType, byte[] bytes) {
    }

    private static String decodeKey(String encodedKey) {
        try {
            return new String(Base64.getUrlDecoder().decode(encodedKey), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_STORAGE_KEY", "资源键编码无效");
        }
    }

    private static String sanitize(String filename) {
        String value = filename == null ? "file" : filename.trim();
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank()) {
            value = "file";
        }
        return value.length() > 80 ? value.substring(0, 80) : value;
    }
}
