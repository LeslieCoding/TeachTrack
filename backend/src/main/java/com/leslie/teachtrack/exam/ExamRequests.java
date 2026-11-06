package com.leslie.teachtrack.exam;

import java.util.List;

public record ExamRequests() {
    public record ImportRequest(
            String studentId,
            String subjectId,
            String examName,
            String examDate,
            Double fullScore,
            Double score,
            String assetId
    ) {
    }

    public record ParsedQuestionInput(
            String number,
            String type,
            Double score,
            Double fullScore,
            String studentAnswer,
            String standardAnswer,
            String stem
    ) {
    }

    public record AnalyzeRequest(String text, List<ParsedQuestionInput> questions) {
    }

    public record UpdateAnswerRequest(
            Double score,
            Double fullScore,
            String studentAnswer,
            String teacherComment,
            String mistakeType,
            String mistakeReason
    ) {
    }
}
