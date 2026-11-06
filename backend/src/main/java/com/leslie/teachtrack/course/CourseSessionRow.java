package com.leslie.teachtrack.course;

import java.time.LocalDate;
import java.util.UUID;

public record CourseSessionRow(
        UUID id,
        String legacyId,
        UUID studentId,
        LocalDate date,
        String startTime,
        String endTime,
        String lessonTitle,
        String lessonContent,
        String performance,
        String problems,
        String homeworkSummary,
        String nextLessonPlan,
        String teacherNotes,
        int version
) {
}
