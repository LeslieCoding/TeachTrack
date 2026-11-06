package com.leslie.teachtrack.question;

import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.PageResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {
    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<QuestionDetail> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody QuestionAnswer.CreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal));
    }

    @GetMapping
    public PageResponse<QuestionRow> search(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) Integer minDifficulty,
            @RequestParam(required = false) Integer maxDifficulty,
            @RequestParam(required = false) UUID knowledgePointId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return service.search(subjectId, grade, questionType, minDifficulty, maxDifficulty,
                knowledgePointId, sourceType, reviewStatus, query, limit, offset);
    }

    @GetMapping("/{id}")
    public QuestionDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/duplicates")
    public QuestionService.DuplicateCheck duplicates(@RequestParam String stem) {
        return service.duplicates(stem);
    }

    @GetMapping("/{id}/assets")
    public java.util.List<QuestionAsset> assets(@PathVariable UUID id) {
        return service.assets(id);
    }

    public record AssetLinkRequest(UUID assetId, String assetType, Integer sortOrder) {
    }

    @PostMapping("/{id}/assets")
    public ResponseEntity<Void> linkAsset(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody AssetLinkRequest request
    ) {
        service.linkAsset(id, request.assetId(), request.assetType(),
                request.sortOrder() == null ? 0 : request.sortOrder(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/{id}")
    public QuestionDetail update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody QuestionAnswer.UpdateRequest request
    ) {
        return service.update(id, request, principal);
    }

    @PostMapping("/{id}/review")
    public QuestionDetail review(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody QuestionAnswer.ReviewRequest request
    ) {
        return service.review(id, request.status(), principal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id
    ) {
        service.softDelete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
