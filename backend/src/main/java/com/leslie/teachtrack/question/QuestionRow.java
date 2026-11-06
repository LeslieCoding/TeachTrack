package com.leslie.teachtrack.question;

import java.time.Instant;
import java.util.UUID;

public record QuestionRow(
        UUID id,
        String legacyId,
        UUID subjectId,
        String grade,
        String textbookVersion,
        String questionType,
        int difficulty,
        String stem,
        String sourceType,
        String sourceName,
        String reviewStatus,
        String copyrightStatus,
        String usageScope,
        int version,
        Instant updatedAt
) {
}
