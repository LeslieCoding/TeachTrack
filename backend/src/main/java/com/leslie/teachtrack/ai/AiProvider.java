package com.leslie.teachtrack.ai;

import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral AI boundary. Business code never imports a vendor SDK.
 */
public interface AiProvider {
    ExamAnalysis analyzeExam(ExamAnalysisRequest request);

    List<GeneratedQuestion> generateQuestions(QuestionGenerationRequest request);

    QuestionReview reviewQuestion(QuestionReviewRequest request);

    String generateExplanation(ExplanationRequest request);

    record ExamAnalysisRequest(UUID studentId, String subject, String examName, List<String> stems) {
    }

    record AnalyzedQuestion(String number, List<String> knowledgeNames, String mistakeType,
                            String reason, double confidence) {
    }

    record ExamAnalysis(List<AnalyzedQuestion> questions, String summary, String provider, String model) {
    }

    record QuestionGenerationRequest(UUID subjectId, String grade, List<String> knowledgeNames,
                                     int count, Integer minDifficulty, Integer maxDifficulty, String mode) {
    }

    record GeneratedQuestion(String stem, String questionType, int difficulty, String standardAnswer,
                             String analysis, List<String> knowledgeNames, String sourceType) {
    }

    record QuestionReviewRequest(String stem, String standardAnswer, String analysis) {
    }

    record QuestionReview(boolean acceptable, List<String> problems) {
    }

    record ExplanationRequest(String stem, String standardAnswer) {
    }
}
