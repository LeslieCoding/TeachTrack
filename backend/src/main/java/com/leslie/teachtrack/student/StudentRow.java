package com.leslie.teachtrack.student;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record StudentRow(
        UUID id,
        String legacyId,
        String fullName,
        String displayName,
        String status,
        String gender,
        String currentGrade,
        String gradeAtRecord,
        String nameCompleteness,
        String school,
        String className,
        UUID subjectId,
        String notes,
        int version,
        Instant updatedAt
) {
    public Optional<String> legacyIdOptional() {
        return Optional.ofNullable(legacyId);
    }
}
