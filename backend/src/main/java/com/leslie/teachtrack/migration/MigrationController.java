package com.leslie.teachtrack.migration;

import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/migrations")
public class MigrationController {
    public record MigrationRequest(String path, String format, Boolean dryRun) {
    }

    private final MigrationService service;
    private final Path allowedRoot;

    public MigrationController(
            MigrationService service,
            @Value("${teachtrack.migration.allowed-root:./}") String allowedRoot
    ) {
        this.service = service;
        this.allowedRoot = Path.of(allowedRoot).toAbsolutePath().normalize();
    }

    @PostMapping
    public ResponseEntity<MigrationService.Result> migrate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody MigrationRequest request
    ) {
        if (principal == null || !principal.hasScope("owner")) {
            throw ApiException.forbidden("仅 OWNER 可执行迁移");
        }
        if (request.path() == null || request.path().isBlank()) {
            throw ApiException.badRequest("MIGRATION_PATH_REQUIRED", "path 必填");
        }
        Path source = Path.of(request.path()).toAbsolutePath().normalize();
        if (!source.startsWith(allowedRoot)) {
            throw ApiException.forbidden("迁移文件必须位于 " + allowedRoot);
        }
        if (!Files.isRegularFile(source)) {
            throw ApiException.notFound("迁移文件不存在: " + source);
        }
        try {
            if (Files.size(source) > 200L * 1024 * 1024) {
                throw ApiException.badRequest("FILE_TOO_LARGE", "迁移文件最大 200 MB");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取迁移文件大小", exception);
        }
        MigrationService.Result result = service.migrate(
                source, request.format(), Boolean.TRUE.equals(request.dryRun()), principal.userId());
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
