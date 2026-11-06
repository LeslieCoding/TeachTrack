package com.leslie.teachtrack.sync;

import com.leslie.teachtrack.auth.AuthPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    public record PushRequest(String deviceId, List<SyncService.Operation> operations) {
    }

    private final SyncChangeRepository repository;
    private final SyncService service;

    public SyncController(SyncChangeRepository repository, SyncService service) {
        this.repository = repository;
        this.service = service;
    }

    @PostMapping("/operations")
    public SyncService.Result push(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody PushRequest request
    ) {
        if (request.operations() == null || request.operations().isEmpty()) {
            return new SyncService.Result(List.of(), List.of());
        }
        if (request.operations().size() > 200) {
            throw com.leslie.teachtrack.common.ApiException.badRequest(
                    "BATCH_TOO_LARGE", "单次最多 200 条同步操作");
        }
        return service.push(principal, request.deviceId(), request.operations());
    }

    @GetMapping("/changes")
    public Map<String, Object> changes(
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "500") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        List<SyncChangeRepository.SyncChange> changes = repository.list(Math.max(0, cursor), safeLimit);
        long next = changes.isEmpty() ? Math.max(0, cursor) : changes.get(changes.size() - 1).seq();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("changes", changes);
        body.put("nextCursor", next);
        body.put("hasMore", changes.size() == safeLimit);
        return body;
    }
}
