package com.leslie.teachtrack.course;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseSessionRepository {
    private final JdbcTemplate jdbc;

    public CourseSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CourseSessionRow> findByStudent(UUID studentId, int limit) {
        return jdbc.query("""
                select id, legacy_id, student_id, date, start_time, end_time, lesson_title,
                       lesson_content, performance, problems, homework_summary, next_lesson_plan,
                       teacher_notes, version
                from course_sessions
                where student_id = ? and deleted_at is null
                order by date desc, start_time desc
                limit ?
                """, (rs, rowNum) -> new CourseSessionRow(
                rs.getObject("id", UUID.class),
                rs.getString("legacy_id"),
                rs.getObject("student_id", UUID.class),
                rs.getObject("date", java.time.LocalDate.class),
                rs.getString("start_time"),
                rs.getString("end_time"),
                rs.getString("lesson_title"),
                rs.getString("lesson_content"),
                rs.getString("performance"),
                rs.getString("problems"),
                rs.getString("homework_summary"),
                rs.getString("next_lesson_plan"),
                rs.getString("teacher_notes"),
                rs.getInt("version")
        ), studentId, limit);
    }
}
