package com.leslie.teachtrack.knowledge;

import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.common.Ids;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeService {
    public record SubjectRow(UUID id, String code, String name, String stage, int sortOrder) {
    }

    public record CreateKnowledgeRequest(
            UUID parentId,
            UUID subjectId,
            String grade,
            String textbookVersion,
            String chapter,
            String section,
            String name,
            String code,
            String description,
            Integer sortOrder
    ) {
    }

    private final JdbcTemplate jdbc;

    public KnowledgeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SubjectRow> subjects() {
        return jdbc.query("""
                select id, code, name, stage, sort_order from subjects order by sort_order, name
                """, (rs, rowNum) -> new SubjectRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("stage"),
                rs.getInt("sort_order")
        ));
    }

    public List<KnowledgePointRow> search(UUID subjectId, String query, int limit) {
        StringBuilder sql = new StringBuilder("""
                select id, legacy_id, parent_id, subject_id, grade, textbook_version, chapter, section,
                       name, code, description, sort_order
                from knowledge_points where deleted_at is null
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (subjectId != null) {
            sql.append(" and subject_id = ?");
            args.add(subjectId);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" and name ilike ?");
            args.add("%" + query + "%");
        }
        sql.append(" order by sort_order, name limit ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbc.query(sql.toString(), (rs, rowNum) -> new KnowledgePointRow(
                rs.getObject("id", UUID.class),
                rs.getString("legacy_id"),
                rs.getObject("parent_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getString("grade"),
                rs.getString("textbook_version"),
                rs.getString("chapter"),
                rs.getString("section"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("description"),
                rs.getInt("sort_order")
        ), args.toArray());
    }

    @Transactional
    public KnowledgePointRow create(CreateKnowledgeRequest request, AuthPrincipal principal) {
        if (request.subjectId() == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("INVALID_KNOWLEDGE_POINT", "学科与名称必填");
        }
        Integer subjectCount = jdbc.queryForObject(
                "select count(*) from subjects where id = ?", Integer.class, request.subjectId());
        if (subjectCount == null || subjectCount == 0) {
            throw ApiException.badRequest("SUBJECT_NOT_FOUND", "学科不存在");
        }
        String code = request.code();
        if (code == null || code.isBlank()) {
            code = "KP-" + Ids.sha256Hex(request.subjectId() + ":" + request.name()).substring(0, 12);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into knowledge_points (id, parent_id, subject_id, grade, textbook_version, chapter,
                    section, name, code, description, sort_order)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, request.parentId(), request.subjectId(),
                nullToEmpty(request.grade()), nullToEmpty(request.textbookVersion()),
                nullToEmpty(request.chapter()), nullToEmpty(request.section()),
                request.name().trim(), code, nullToEmpty(request.description()),
                request.sortOrder() == null ? 0 : request.sortOrder());
        return jdbc.query("""
                select id, legacy_id, parent_id, subject_id, grade, textbook_version, chapter, section,
                       name, code, description, sort_order
                from knowledge_points where id = ?
                """, (rs, rowNum) -> new KnowledgePointRow(
                rs.getObject("id", UUID.class),
                rs.getString("legacy_id"),
                rs.getObject("parent_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getString("grade"),
                rs.getString("textbook_version"),
                rs.getString("chapter"),
                rs.getString("section"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getString("description"),
                rs.getInt("sort_order")
        ), id).get(0);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
