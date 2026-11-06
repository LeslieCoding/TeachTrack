package com.leslie.teachtrack.exam;

import com.leslie.teachtrack.auth.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ExamController {
    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    @PostMapping("/exams/import")
    public ResponseEntity<ExamPaperRow> importPaper(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody ExamRequests.ImportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importPaper(request, principal));
    }

    @GetMapping("/students/{studentId}/exams")
    public List<ExamService.ExamListRow> list(
            @PathVariable String studentId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return service.listByStudent(studentId, limit);
    }

    @PostMapping("/exams/{id}/analyze")
    public ExamPaperRow.ExamDetail analyze(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestBody ExamRequests.AnalyzeRequest request
    ) {
        return service.analyze(id, request, principal);
    }

    @GetMapping("/exams/{id}/analysis")
    public ExamPaperRow.ExamDetail analysis(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PatchMapping("/exams/{id}/questions/{examQuestionId}")
    public ExamPaperRow.ExamDetail updateQuestion(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID examQuestionId,
            @RequestBody ExamRequests.UpdateAnswerRequest request
    ) {
        return service.updateAnswer(id, examQuestionId, request, principal);
    }

    @PostMapping("/exams/{id}/approve")
    public ExamPaperRow.ExamDetail approve(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean importToBank
    ) {
        return service.approve(id, importToBank, principal);
    }
}
