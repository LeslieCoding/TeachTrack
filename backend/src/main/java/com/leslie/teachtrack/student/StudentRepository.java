package com.leslie.teachtrack.student;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentRepository {
    private final JdbcTemplate jdbc;

    public StudentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StudentRow> list(String status, String query, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                select id, legacy_id, full_name, display_name, status, gender, current_grade,
                       grade_at_record, name_completeness, school, class_name, subject_id, notes,
                       version, updated_at
                from students where deleted_at is null
                """);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" and (display_name ilike ? or coalesce(full_name,'') ilike ?)");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        sql.append(" order by updated_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, rowNum) -> new StudentRow(
                rs.getObject("id", UUID.class),
                rs.getString("legacy_id"),
                rs.getString("full_name"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("gender"),
                rs.getString("current_grade"),
                rs.getString("grade_at_record"),
                rs.getString("name_completeness"),
                rs.getString("school"),
                rs.getString("class_name"),
                rs.getObject("subject_id", UUID.class),
                rs.getString("notes"),
                rs.getInt("version"),
                rs.getTimestamp("updated_at").toInstant()
        ), args.toArray());
    }

    public long count(String status, String query) {
        StringBuilder sql = new StringBuilder("select count(*) from students where deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" and (display_name ilike ? or coalesce(full_name,'') ilike ?)");
            args.add("%" + query + "%");
            args.add("%" + query + "%");
        }
        Long value = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    public Optional<StudentRow> find(String idOrLegacyId) {
        return jdbc.query("""
                select id, legacy_id, full_name, display_name, status, gender, current_grade,
                       grade_at_record, name_completeness, school, class_name, subject_id, notes,
                       version, updated_at
                from students
                where deleted_at is null and (id::text = ? or legacy_id = ?)
                """, (rs, rowNum) -> new StudentRow(
                rs.getObject("id", UUID.class),
                rs.getString("legacy_id"),
                rs.getString("full_name"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("gender"),
                rs.getString("current_grade"),
                rs.getString("grade_at_record"),
                rs.getString("name_completeness"),
                rs.getString("school"),
                rs.getString("class_name"),
                rs.getObject("subject_id", UUID.class),
                rs.getString("notes"),
                rs.getInt("version"),
                rs.getTimestamp("updated_at").toInstant()
        ), idOrLegacyId, idOrLegacyId).stream().findFirst();
    }
}
