package com.leslie.teachtrack.practice;

import com.leslie.teachtrack.auth.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PracticeController {
    private final PracticeService service;
    private final PracticeRepository repository;
    private final PracticeExportService exportService;

    public PracticeController(PracticeService service, PracticeRepository repository,
                              PracticeExportService exportService) {
        this.service = service;
        this.repository = repository;
        this.exportService = exportService;
    }

    @PostMapping("/practices/generate")
    public ResponseEntity<PracticeModels.PracticeDetail> generate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody PracticeModels.GenerateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(request, principal));
    }

    @GetMapping("/practices/{id}")
    public PracticeModels.PracticeDetail get(@PathVariable UUID id) {
        return service.detail(id);
    }

    @GetMapping("/practices/{id}/export")
    public PracticeExportService.ExportResult export(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "STUDENT") String variant,
            @RequestParam(defaultValue = "PDF") String format
    ) {
        PracticeExportService.ExportResult result = exportService.export(id, variant, format, principal);
        String url = result.downloadUrl();
        if (url.startsWith("/")) {
            url = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                    .fromCurrentContextPath().toUriString() + url;
        }
        return new PracticeExportService.ExportResult(result.assetId(), result.format(), result.variant(), url);
    }

    @PostMapping("/practices/{id}/approve")
    public PracticeModels.PracticeDetail approve(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id
    ) {
        return service.approve(id, principal);
    }

    @PostMapping("/practices/{id}/submit")
    public PracticeModels.PracticeDetail submit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody PracticeModels.SubmitRequest request
    ) {
        return service.submit(id, request, principal);
    }

    @GetMapping("/students/{studentId}/practices")
    public List<PracticeModels.PracticeSetRow> list(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return service.listByStudent(studentId, limit);
    }

    @GetMapping("/students/{studentId}/mastery")
    public List<MasteryModels.MasteryRow> mastery(@PathVariable UUID studentId) {
        return repository.mastery(studentId);
    }
}
