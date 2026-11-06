package com.leslie.teachtrack.storage;

import com.leslie.teachtrack.auth.AuthPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
    public record UploadRequest(String ownerType, UUID ownerId, String filename, String contentType, long sizeBytes) {
    }

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @PostMapping("/upload-url")
    public Map<String, Object> uploadUrl(@RequestBody UploadRequest request) {
        AssetService.UploadTicket ticket = service.createUpload(
                request.ownerType(), request.ownerId(), request.filename(),
                request.contentType(), request.sizeBytes());
        return Map.of(
                "assetId", ticket.assetId(),
                "uploadUrl", absolute(ticket.presigned().url()),
                "method", ticket.presigned().method(),
                "expiresAt", ticket.presigned().expiresAt()
        );
    }

    @PutMapping(value = "/upload/{encodedKey}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> upload(
            @PathVariable String encodedKey,
            @RequestParam("exp") long expiresAt,
            @RequestParam("sig") String signature,
            @RequestBody(required = false) byte[] data
    ) {
        service.completeUpload(encodedKey, expiresAt, signature, data);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{assetId}/download-url")
    public Map<String, Object> downloadUrl(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID assetId
    ) {
        PresignedUrl presigned = service.downloadUrl(assetId);
        service.recordRead(principal, assetId);
        return Map.of(
                "url", absolute(presigned.url()),
                "method", presigned.method(),
                "expiresAt", presigned.expiresAt()
        );
    }

    @GetMapping("/download/{encodedKey}")
    public ResponseEntity<byte[]> download(
            @PathVariable String encodedKey,
            @RequestParam("exp") long expiresAt,
            @RequestParam("sig") String signature
    ) {
        AssetService.DownloadedAsset asset = service.download(encodedKey, expiresAt, signature);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.bytes());
    }

    private static String absolute(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() + url;
    }
}
