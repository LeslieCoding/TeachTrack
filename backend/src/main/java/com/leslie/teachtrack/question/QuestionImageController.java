package com.leslie.teachtrack.question;

import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.storage.AssetService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/questions/{questionId}")
public class QuestionImageController {
    public record ImageView(UUID assetId, String url) {
    }

    private final QuestionRepository repository;
    private final AssetService assets;
    private final AuditService audit;

    public QuestionImageController(QuestionRepository repository, AssetService assets, AuditService audit) {
        this.repository = repository;
        this.assets = assets;
        this.audit = audit;
    }

    @GetMapping("/images")
    public List<ImageView> images(@PathVariable UUID questionId) {
        requireQuestion(questionId);
        return repository.assets(questionId).stream()
                .filter(asset -> List.of("IMAGE", "DIAGRAM", "GRAPH").contains(asset.assetType()))
                .map(asset -> new ImageView(asset.assetId(),
                        absolute(assets.downloadUrl(asset.assetId()).url())))
                .toList();
    }

    @PostMapping(value = "/image", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID questionId,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, defaultValue = "image/png") String contentType,
            @RequestBody(required = false) byte[] data
    ) {
        requireQuestion(questionId);
        String mime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!mime.startsWith("image/")) {
            throw ApiException.badRequest("INVALID_IMAGE_TYPE", "题图必须是图片文件");
        }
        String extension = switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
        UUID assetId = assets.uploadDirect("QUESTION", questionId, "question-image" + extension, mime, data);
        int sortOrder = repository.assets(questionId).size();
        repository.linkAsset(questionId, assetId, "IMAGE", sortOrder);
        audit.record(principal, "question.uploadImage", "Question", questionId, "SUCCESS", null);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "assetId", assetId,
                "url", absolute(assets.downloadUrl(assetId).url())
        ));
    }

    private void requireQuestion(UUID questionId) {
        repository.find(questionId, false)
                .orElseThrow(() -> ApiException.notFound("题目不存在: " + questionId));
    }

    private static String absolute(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() + url;
    }
}
