package com.leslie.teachtrack.question;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {
    private final JdbcTemplate jdbc;

    public QuestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<QuestionDetail> find(UUID id, boolean includeDeleted) {
        String sql = """
                select id, legacy_id, subject_id, grade, textbook_version, question_type, difficulty,
                       stem, source_type, source_name, review_status, copyright_status, usage_scope,
                       version, updated_at, ability_tags
                from questions where id = ? %s
                """.formatted(includeDeleted ? "" : "and deleted_at is null");
        Optional<QuestionRow> row = jdbc.query(sql, (rs, rowNum) -> map(rs), id).stream().findFirst();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        QuestionRow question = row.get();
        return Optional.of(new QuestionDetail(
                question,
                options(id),
                answer(id).orElse(null),
                knowledge(id),
                abilityTags(id)
        ));
    }

    public Optional<QuestionRow> findByStemHash(String stemHash) {
        return jdbc.query("""
                select id, legacy_id, subject_id, grade, textbook_version, question_type, difficulty,
                       stem, source_type, source_name, review_status, copyright_status, usage_scope,
                       version, updated_at
                from questions where stem_hash = ? and deleted_at is null limit 1
                """, (rs, rowNum) -> map(rs), stemHash).stream().findFirst();
    }

    public Optional<QuestionRow> findByNormalizedHash(String normalizedHash, UUID excludeId) {
        return jdbc.query("""
                select id, legacy_id, subject_id, grade, textbook_version, question_type, difficulty,
                       stem, source_type, source_name, review_status, copyright_status, usage_scope,
                       version, updated_at
                from questions
                where normalized_hash = ? and deleted_at is null and (?::uuid is null or id <> ?::uuid)
                limit 1
                """, (rs, rowNum) -> map(rs), normalizedHash, excludeId, excludeId).stream().findFirst();
    }

    public List<QuestionRow> findSimilar(String stem, int limit) {
        return jdbc.query("""
                select id, legacy_id, subject_id, grade, textbook_version, question_type, difficulty,
                       stem, source_type, source_name, review_status, copyright_status, usage_scope,
                       version, updated_at
                from questions
                where deleted_at is null and similarity(stem, ?) > 0.25
                order by similarity(stem, ?) desc
                limit ?
                """, (rs, rowNum) -> map(rs), stem, stem, Math.max(1, Math.min(limit, 20)));
    }

    public void linkAsset(UUID questionId, UUID assetId, String assetType, int sortOrder) {
        jdbc.update("""
                insert into question_assets (id, question_id, asset_id, asset_type, sort_order)
                values (?, ?, ?, ?, ?)
                on conflict (question_id, asset_id) do nothing
                """, UUID.randomUUID(), questionId, assetId, assetType, sortOrder);
    }

    public List<QuestionAsset> assets(UUID questionId) {
        return jdbc.query("""
                select asset_id, asset_type, sort_order from question_assets
                where question_id = ? order by sort_order
                """, (rs, rowNum) -> new QuestionAsset(
                rs.getObject("asset_id", UUID.class),
                rs.getString("asset_type"),
                rs.getInt("sort_order")
        ), questionId);
    }

    public void insert(QuestionRow row, String stemHash, String normalizedHash, List<String> abilityTags, String createdBy) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into questions (id, legacy_id, subject_id, grade, textbook_version, question_type,
                        difficulty, stem, stem_hash, normalized_hash, source_type, source_name,
                        copyright_status, usage_scope, review_status, ability_tags, created_by, version)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """);
            statement.setObject(1, row.id());
            statement.setString(2, row.legacyId());
            statement.setObject(3, row.subjectId());
            statement.setString(4, row.grade());
            statement.setString(5, row.textbookVersion());
            statement.setString(6, row.questionType());
            statement.setInt(7, row.difficulty());
            statement.setString(8, row.stem());
            statement.setString(9, stemHash);
            statement.setString(10, normalizedHash);
            statement.setString(11, row.sourceType());
            statement.setString(12, row.sourceName());
            statement.setString(13, row.copyrightStatus());
            statement.setString(14, row.usageScope());
            statement.setString(15, row.reviewStatus());
            Array tags = connection.createArrayOf("text", abilityTags == null ? new Object[0] : abilityTags.toArray());
            statement.setArray(16, tags);
            statement.setString(17, createdBy);
            return statement;
        });
    }

    public int update(UUID id, int baseVersion, QuestionRow row, String stemHash, String normalizedHash,
                      List<String> abilityTags, String updatedBy) {
        return jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    update questions
                    set subject_id = ?, grade = ?, textbook_version = ?, question_type = ?, difficulty = ?,
                        stem = ?, stem_hash = ?, normalized_hash = ?, source_type = ?, source_name = ?,
                        copyright_status = ?, usage_scope = ?, review_status = ?, ability_tags = ?,
                        updated_by = ?, version = version + 1, updated_at = now()
                    where id = ? and version = ? and deleted_at is null
                    """);
            statement.setObject(1, row.subjectId());
            statement.setString(2, row.grade());
            statement.setString(3, row.textbookVersion());
            statement.setString(4, row.questionType());
            statement.setInt(5, row.difficulty());
            statement.setString(6, row.stem());
            statement.setString(7, stemHash);
            statement.setString(8, normalizedHash);
            statement.setString(9, row.sourceType());
            statement.setString(10, row.sourceName());
            statement.setString(11, row.copyrightStatus());
            statement.setString(12, row.usageScope());
            statement.setString(13, row.reviewStatus());
            Array tags = connection.createArrayOf("text", abilityTags == null ? new Object[0] : abilityTags.toArray());
            statement.setArray(14, tags);
            statement.setString(15, updatedBy);
            statement.setObject(16, id);
            statement.setInt(17, baseVersion);
            return statement;
        });
    }

    public int updateReviewStatus(UUID id, String status) {
        return jdbc.update("""
                update questions set review_status = ?, version = version + 1, updated_at = now()
                where id = ? and deleted_at is null
                """, status, id);
    }

    public int softDelete(UUID id) {
        return jdbc.update("""
                update questions set deleted_at = now(), version = version + 1, updated_at = now()
                where id = ? and deleted_at is null
                """, id);
    }

    public void insertVersion(UUID questionId, int version, String snapshotJson, String changeNote, String createdBy) {
        jdbc.update("""
                insert into question_versions (id, question_id, version, snapshot, change_note, created_by)
                values (?, ?, ?, ?::jsonb, ?, ?)
                """, UUID.randomUUID(), questionId, version, snapshotJson, changeNote, createdBy);
    }

    public void replaceOptions(UUID questionId, List<QuestionAnswer.OptionInput> options) {
        jdbc.update("delete from question_options where question_id = ?", questionId);
        if (options == null) {
            return;
        }
        int sort = 0;
        for (QuestionAnswer.OptionInput option : options) {
            jdbc.update("""
                    insert into question_options (id, question_id, option_key, content, sort_order)
                    values (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), questionId, option.key(), option.content(),
                    option.sortOrder() == null ? sort : option.sortOrder());
            sort++;
        }
    }

    public void upsertAnswer(UUID questionId, QuestionAnswer answer) {
        if (answer == null) {
            jdbc.update("delete from question_answers where question_id = ?", questionId);
            return;
        }
        jdbc.update("""
                insert into question_answers (question_id, standard_answer, analysis, scoring_points, solution_steps)
                values (?, ?, ?, ?, ?)
                on conflict (question_id) do update set
                    standard_answer = excluded.standard_answer,
                    analysis = excluded.analysis,
                    scoring_points = excluded.scoring_points,
                    solution_steps = excluded.solution_steps,
                    updated_at = now()
                """, questionId, answer.standardAnswer(), nullToEmpty(answer.analysis()),
                nullToEmpty(answer.scoringPoints()), nullToEmpty(answer.solutionSteps()));
    }

    public void replaceKnowledge(UUID questionId, List<QuestionAnswer.KnowledgeLink> links) {
        jdbc.update("delete from question_knowledge where question_id = ?", questionId);
        if (links == null) {
            return;
        }
        for (QuestionAnswer.KnowledgeLink link : links) {
            jdbc.update("""
                    insert into question_knowledge (question_id, knowledge_point_id, importance)
                    values (?, ?, ?) on conflict do nothing
                    """, questionId, link.knowledgePointId(), link.importance() == null ? "PRIMARY" : link.importance());
        }
    }

    public List<QuestionRow> search(
            UUID subjectId, String grade, String questionType, Integer minDifficulty, Integer maxDifficulty,
            UUID knowledgePointId, String sourceType, String reviewStatus, String query,
            int limit, int offset
    ) {
        StringBuilder sql = new StringBuilder("""
                select distinct q.id, q.legacy_id, q.subject_id, q.grade, q.textbook_version, q.question_type,
                       q.difficulty, q.stem, q.source_type, q.source_name, q.review_status,
                       q.copyright_status, q.usage_scope, q.version, q.updated_at
                from questions q
                """);
        List<Object> args = new ArrayList<>();
        if (knowledgePointId != null) {
            sql.append(" join question_knowledge qk on qk.question_id = q.id ");
        }
        sql.append(" where q.deleted_at is null ");
        if (subjectId != null) {
            sql.append(" and q.subject_id = ? ");
            args.add(subjectId);
        }
        if (grade != null && !grade.isBlank()) {
            sql.append(" and q.grade = ? ");
            args.add(grade);
        }
        if (questionType != null && !questionType.isBlank()) {
            sql.append(" and q.question_type = ? ");
            args.add(questionType);
        }
        if (minDifficulty != null) {
            sql.append(" and q.difficulty >= ? ");
            args.add(minDifficulty);
        }
        if (maxDifficulty != null) {
            sql.append(" and q.difficulty <= ? ");
            args.add(maxDifficulty);
        }
        if (knowledgePointId != null) {
            sql.append(" and qk.knowledge_point_id = ? ");
            args.add(knowledgePointId);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" and q.source_type = ? ");
            args.add(sourceType);
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            sql.append(" and q.review_status = ? ");
            args.add(reviewStatus);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" and q.stem ilike ? ");
            args.add("%" + query + "%");
        }
        sql.append(" order by q.updated_at desc limit ? offset ? ");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, rowNum) -> map(rs), args.toArray());
    }

    public long count(
            UUID subjectId, String grade, String questionType, Integer minDifficulty, Integer maxDifficulty,
            UUID knowledgePointId, String sourceType, String reviewStatus, String query
    ) {
        StringBuilder sql = new StringBuilder("select count(distinct q.id) from questions q ");
        List<Object> args = new ArrayList<>();
        if (knowledgePointId != null) {
            sql.append(" join question_knowledge qk on qk.question_id = q.id ");
        }
        sql.append(" where q.deleted_at is null ");
        if (subjectId != null) {
            sql.append(" and q.subject_id = ? ");
            args.add(subjectId);
        }
        if (grade != null && !grade.isBlank()) {
            sql.append(" and q.grade = ? ");
            args.add(grade);
        }
        if (questionType != null && !questionType.isBlank()) {
            sql.append(" and q.question_type = ? ");
            args.add(questionType);
        }
        if (minDifficulty != null) {
            sql.append(" and q.difficulty >= ? ");
            args.add(minDifficulty);
        }
        if (maxDifficulty != null) {
            sql.append(" and q.difficulty <= ? ");
            args.add(maxDifficulty);
        }
        if (knowledgePointId != null) {
            sql.append(" and qk.knowledge_point_id = ? ");
            args.add(knowledgePointId);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" and q.source_type = ? ");
            args.add(sourceType);
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            sql.append(" and q.review_status = ? ");
            args.add(reviewStatus);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" and q.stem ilike ? ");
            args.add("%" + query + "%");
        }
        Long value = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return value == null ? 0 : value;
    }

    private List<QuestionOption> options(UUID questionId) {
        return jdbc.query("""
                select id, option_key, content, sort_order from question_options
                where question_id = ? order by sort_order, option_key
                """, (rs, rowNum) -> new QuestionOption(
                rs.getObject("id", UUID.class),
                rs.getString("option_key"),
                rs.getString("content"),
                rs.getInt("sort_order")
        ), questionId);
    }

    private Optional<QuestionAnswer> answer(UUID questionId) {
        return jdbc.query("""
                select standard_answer, analysis, scoring_points, solution_steps
                from question_answers where question_id = ?
                """, (rs, rowNum) -> new QuestionAnswer(
                rs.getString("standard_answer"),
                rs.getString("analysis"),
                rs.getString("scoring_points"),
                rs.getString("solution_steps")
        ), questionId).stream().findFirst();
    }

    private List<QuestionKnowledge> knowledge(UUID questionId) {
        return jdbc.query("""
                select knowledge_point_id, importance from question_knowledge
                where question_id = ? order by importance
                """, (rs, rowNum) -> new QuestionKnowledge(
                rs.getObject("knowledge_point_id", UUID.class),
                rs.getString("importance")
        ), questionId);
    }

    private List<String> abilityTags(UUID questionId) {
        return jdbc.query("select ability_tags from questions where id = ?", (rs, rowNum) -> {
            Array array = rs.getArray("ability_tags");
            if (array == null) {
                return List.<String>of();
            }
            Object value = array.getArray();
            if (value instanceof String[] strings) {
                return List.of(strings);
            }
            return List.<String>of();
        }, questionId).stream().findFirst().orElse(List.of());
    }

    private static QuestionRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QuestionRow(
                rs.getObject("id", UUID.class),
                rs.getString("legacy_id"),
                rs.getObject("subject_id", UUID.class),
                rs.getString("grade"),
                rs.getString("textbook_version"),
                rs.getString("question_type"),
                rs.getInt("difficulty"),
                rs.getString("stem"),
                rs.getString("source_type"),
                rs.getString("source_name"),
                rs.getString("review_status"),
                rs.getString("copyright_status"),
                rs.getString("usage_scope"),
                rs.getInt("version"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
