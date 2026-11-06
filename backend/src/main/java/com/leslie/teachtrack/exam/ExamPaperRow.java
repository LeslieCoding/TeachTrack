package com.leslie.teachtrack.exam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExamPaperRow(
        UUID id,
        UUID studentId,
        UUID subjectId,
        String examName,
        LocalDate examDate,
        Double fullScore,
        Double score,
        UUID storageAssetId,
        String analysisStatus,
        int version
) {
    public record ExamQuestionRow(
            UUID id,
            UUID examPaperId,
            UUID questionId,
            String questionNumber,
            String questionType,
            double score,
            double fullScore,
            int sortOrder,
            String stem,
            String standardAnswer
    ) {
    }

    public record StudentAnswerRow(
            UUID id,
            UUID examQuestionId,
            String studentAnswer,
            String standardAnswer,
            double score,
            double fullScore,
            Boolean isCorrect,
            String teacherComment,
            String aiComment
    ) {
    }

    public record AnalysisRow(
            UUID id,
            UUID examQuestionId,
            String ability,
            String knowledgeSummary,
            String mistakeType,
            String mistakeReason,
            Double confidence,
            Integer difficulty,
            boolean taughtBefore,
            java.time.Instant lastTaughtAt,
            boolean wrongBefore,
            int repeatCount,
            String suggestedTraining,
            double potentialScoreGain,
            String reviewStatus
    ) {
    }

    public record ExamDetail(
            ExamPaperRow paper,
            List<ExamQuestionRow> questions,
            List<StudentAnswerRow> answers,
            List<AnalysisRow> analyses,
            String reportJson
    ) {
    }
}
