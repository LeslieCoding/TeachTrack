package com.leslie.teachtrack.knowledge;

import com.leslie.teachtrack.auth.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/subjects")
    public List<KnowledgeService.SubjectRow> subjects() {
        return service.subjects();
    }

    @GetMapping("/knowledge-points")
    public List<KnowledgePointRow> knowledgePoints(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "200") int limit
    ) {
        return service.search(subjectId, query, limit);
    }

    @PostMapping("/knowledge-points")
    public ResponseEntity<KnowledgePointRow> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody KnowledgeService.CreateKnowledgeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal));
    }
}
