package com.leslie.teachtrack.question;

import java.util.List;
import java.util.UUID;

public record QuestionAnswer(String standardAnswer, String analysis, String scoringPoints, String solutionSteps) {
    public record KnowledgeLink(UUID knowledgePointId, String importance) {
    }

    public record OptionInput(String key, String content, Integer sortOrder) {
    }

    public record CreateRequest(
            UUID subjectId,
            String grade,
            String textbookVersion,
            String questionType,
            int difficulty,
            String stem,
            String sourceType,
            String sourceName,
            String copyrightStatus,
            String usageScope,
            String reviewStatus,
            List<OptionInput> options,
            QuestionAnswer answer,
            List<KnowledgeLink> knowledge,
            List<String> abilityTags
    ) {
    }

    public record UpdateRequest(
            int baseVersion,
            UUID subjectId,
            String grade,
            String textbookVersion,
            String questionType,
            Integer difficulty,
            String stem,
            String sourceType,
            String sourceName,
            String copyrightStatus,
            String usageScope,
            String reviewStatus,
            List<OptionInput> options,
            QuestionAnswer answer,
            List<KnowledgeLink> knowledge,
            List<String> abilityTags
    ) {
    }

    public record ReviewRequest(String status) {
    }
}
