package com.leslie.teachtrack.practice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PracticeModels() {
    public record PracticeSetRow(
            UUID id,
            UUID studentId,
            UUID subjectId,
            String mode,
            String title,
            double totalScore,
            int estimatedMinutes,
            String status,
            int version,
            Instant createdAt
    ) {
    }

    public record PracticeItemRow(
            UUID id,
            UUID questionId,
            String sourceKind,
            String selectionReason,
            double score,
            int sortOrder,
            String stem,
            String questionType,
            int difficulty,
            String reviewStatus
    ) {
    }

    public record GenerateRequest(
            String studentId,
            String subjectId,
            List<String> knowledgePointIds,
            Integer questionCount,
            Integer minDifficulty,
            Integer maxDifficulty,
            Double totalScore,
            Boolean includeMistakes,
            Boolean allowAi,
            String mode,
            String title
    ) {
    }

    public record AnswerInput(UUID itemId, String studentAnswer, Double score, Boolean correct) {
    }

    public record SubmitRequest(List<AnswerInput> answers) {
    }

    public record PracticeDetail(
            PracticeSetRow set,
            List<PracticeItemRow> items,
            List<MasteryModels.MasteryRow> mastery
    ) {
    }
}
