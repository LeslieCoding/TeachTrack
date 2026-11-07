package com.leslie.teachtrack.question;

import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionBulkController {
    public record BulkReviewRequest(List<UUID> ids, String status) {
    }

    public record BulkDeleteRequest(List<UUID> ids) {
    }

    public record BulkResult(int requested, int succeeded, List<String> failed) {
    }

    private final QuestionService service;

    public QuestionBulkController(QuestionService service) {
        this.service = service;
    }

    @PostMapping("/bulk-review")
    public BulkResult review(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody BulkReviewRequest request
    ) {
        return run(request == null ? null : request.ids(), id ->
                service.review(id, request.status(), principal));
    }

    @PostMapping("/bulk-delete")
    public BulkResult delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody BulkDeleteRequest request
    ) {
        return run(request == null ? null : request.ids(), id -> service.softDelete(id, principal));
    }

    private BulkResult run(List<UUID> ids, java.util.function.Consumer<UUID> action) {
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("EMPTY_SELECTION", "请选择至少一道题");
        }
        if (ids.size() > 200) {
            throw ApiException.badRequest("TOO_MANY_ITEMS", "单次最多操作 200 道题");
        }
        List<String> failed = new ArrayList<>();
        int succeeded = 0;
        for (UUID id : ids) {
            try {
                action.accept(id);
                succeeded++;
            } catch (RuntimeException exception) {
                failed.add(id + ": " + exception.getMessage());
            }
        }
        return new BulkResult(ids.size(), succeeded, failed);
    }
}
