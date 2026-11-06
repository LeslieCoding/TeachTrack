package com.leslie.teachtrack.knowledge;

import java.util.UUID;

public record KnowledgePointRow(
        UUID id,
        String legacyId,
        UUID parentId,
        UUID subjectId,
        String grade,
        String textbookVersion,
        String chapter,
        String section,
        String name,
        String code,
        String description,
        int sortOrder
) {
}
