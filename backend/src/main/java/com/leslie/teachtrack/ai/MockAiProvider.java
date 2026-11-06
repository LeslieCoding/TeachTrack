package com.leslie.teachtrack.ai;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic provider used when no model credentials are configured.
 * It never invents questions: generation returns an empty list so the caller
 * can report INSUFFICIENT_QUESTIONS instead of fabricating content.
 */
@Component
public class MockAiProvider implements AiProvider {
    @Override
    public ExamAnalysis analyzeExam(ExamAnalysisRequest request) {
        return new ExamAnalysis(List.of(), "", "mock", "none");
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(QuestionGenerationRequest request) {
        return List.of();
    }

    @Override
    public QuestionReview reviewQuestion(QuestionReviewRequest request) {
        return new QuestionReview(true, List.of());
    }

    @Override
    public String generateExplanation(ExplanationRequest request) {
        return "";
    }
}
