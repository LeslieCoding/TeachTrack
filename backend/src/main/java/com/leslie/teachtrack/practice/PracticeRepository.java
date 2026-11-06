package com.leslie.teachtrack.practice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PracticeRepository {
    private final JdbcTemplate jdbc;

    public PracticeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertSet(UUID id, UUID studentId, UUID subjectId, String mode, String title,
                          String strategyJson, double totalScore, int minutes, String generatedBy) {
        jdbc.update("""
                insert into practice_sets (id, student_id, subject_id, mode, title, strategy, total_score,
                    estimated_minutes, status, generated_by)
                values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'DRAFT', ?)
                """, id, studentId, subjectId, mode, title, strategyJson, totalScore, minutes, generatedBy);
    }

    public java.util.Optional<PracticeModels.PracticeSetRow> findSet(UUID id) {
        return jdbc.query("""
                select id, student_id, subject_id, mode, title, total_score, estimated_minutes, status,
                       version, created_at
                from practice_sets where id = ? and deleted_at is null
                """, (rs, rowNum) -> new PracticeModels.PracticeSetRow(
                rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getString("mode"),
                rs.getString("title"),
                rs.getBigDecimal("total_score").doubleValue(),
                rs.getInt("estimated_minutes"),
                rs.getString("status"),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant()
        ), id).stream().findFirst();
    }

    public void updateStatus(UUID id, String status) {
        jdbc.update("update practice_sets set status = ?, version = version + 1, updated_at = now() where id = ?",
                status, id);
    }

    public void insertItem(UUID id, UUID practiceId, UUID questionId, String sourceKind,
                           String reason, double score, int sortOrder) {
        jdbc.update("""
                insert into practice_items (id, practice_set_id, question_id, source_kind, selection_reason,
                    score, sort_order)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, practiceId, questionId, sourceKind, reason, score, sortOrder);
    }

    public List<PracticeModels.PracticeItemRow> listItems(UUID practiceId) {
        return jdbc.query("""
                select pi.id, pi.question_id, pi.source_kind, pi.selection_reason, pi.score, pi.sort_order,
                       q.stem, q.question_type, q.difficulty, q.review_status
                from practice_items pi join questions q on q.id = pi.question_id
                where pi.practice_set_id = ? order by pi.sort_order
                """, (rs, rowNum) -> new PracticeModels.PracticeItemRow(
                rs.getObject("id", UUID.class),
                rs.getObject("question_id", UUID.class),
                rs.getString("source_kind"),
                rs.getString("selection_reason"),
                rs.getBigDecimal("score").doubleValue(),
                rs.getInt("sort_order"),
                rs.getString("stem"),
                rs.getString("question_type"),
                rs.getInt("difficulty"),
                rs.getString("review_status")
        ), practiceId);
    }

    public List<Candidate> selectCandidates(UUID subjectId, List<UUID> knowledgeIds,
                                            int minDifficulty, int maxDifficulty, int limit) {
        StringBuilder sql = new StringBuilder("""
                select q.id, q.difficulty, q.question_type
                from questions q
                where q.deleted_at is null and q.review_status = 'APPROVED' and q.subject_id = ?
                  and q.difficulty between ? and ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(subjectId);
        args.add(minDifficulty);
        args.add(maxDifficulty);
        if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
            sql.append(" and exists (select 1 from question_knowledge qk where qk.question_id = q.id")
                    .append(" and qk.knowledge_point_id in (")
                    .append(String.join(",", java.util.Collections.nCopies(knowledgeIds.size(), "?")))
                    .append("))");
            args.addAll(knowledgeIds);
        }
        sql.append(" order by q.updated_at desc limit ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, rowNum) -> new Candidate(
                rs.getObject("id", UUID.class),
                rs.getInt("difficulty"),
                rs.getString("question_type")
        ), args.toArray());
    }

    public String standardAnswer(UUID questionId) {
        return jdbc.query("""
                select standard_answer from question_answers where question_id = ?
                """, (rs, rowNum) -> rs.getString("standard_answer"), questionId)
                .stream().findFirst().orElse("");
    }

    public void insertAttempt(UUID id, UUID practiceId, UUID studentId, double totalScore, double fullScore) {
        jdbc.update("""
                insert into practice_attempts (id, practice_set_id, student_id, total_score, full_score)
                values (?, ?, ?, ?, ?)
                """, id, practiceId, studentId, totalScore, fullScore);
    }

    public void updateAttempt(UUID id, double totalScore, double fullScore) {
        jdbc.update("""
                update practice_attempts set total_score = ?, full_score = ? where id = ?
                """, totalScore, fullScore, id);
    }

    public void insertResult(UUID id, UUID attemptId, UUID itemId, String answer, double score,
                             double fullScore, boolean correct) {
        jdbc.update("""
                insert into practice_item_results (id, attempt_id, practice_item_id, student_answer, score,
                    full_score, is_correct)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, attemptId, itemId, answer, score, fullScore, correct);
    }

    public List<MasteryModels.Evidence> evidence(UUID studentId, UUID knowledgePointId) {
        return jdbc.query("""
                select pir.is_correct, q.difficulty, pir.created_at
                from practice_item_results pir
                join practice_attempts pa on pa.id = pir.attempt_id
                join practice_items pi on pi.id = pir.practice_item_id
                join questions q on q.id = pi.question_id
                join question_knowledge qk on qk.question_id = q.id
                where pa.student_id = ? and qk.knowledge_point_id = ?
                order by pir.created_at
                """, (rs, rowNum) -> new MasteryModels.Evidence(
                rs.getBoolean("is_correct"),
                rs.getInt("difficulty"),
                rs.getTimestamp("created_at").toInstant()
        ), studentId, knowledgePointId);
    }

    public List<UUID> knowledgeIds(UUID questionId) {
        return jdbc.query("select knowledge_point_id from question_knowledge where question_id = ?",
                (rs, rowNum) -> rs.getObject("knowledge_point_id", UUID.class), questionId);
    }

    public void upsertMastery(UUID studentId, UUID knowledgePointId, double score, double confidence,
                              int practiceCount, int correctCount, int wrongCount, Instant lastPracticed,
                              String trend) {
        jdbc.update("""
                insert into student_mastery (student_id, knowledge_point_id, mastery_score, confidence,
                    practice_count, correct_count, wrong_count, last_practiced_at, trend, formula_version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::numeric, 'v1')
                on conflict (student_id, knowledge_point_id) do update set
                    mastery_score = excluded.mastery_score, confidence = excluded.confidence,
                    practice_count = excluded.practice_count, correct_count = excluded.correct_count,
                    wrong_count = excluded.wrong_count, last_practiced_at = excluded.last_practiced_at,
                    trend = excluded.trend, formula_version = 'v1', version = student_mastery.version + 1,
                    updated_at = now()
                """, studentId, knowledgePointId, score, confidence, practiceCount, correctCount,
                wrongCount, lastPracticed == null ? null : java.sql.Timestamp.from(lastPracticed), trend);
    }

    public record ExportItem(String stem, String questionType, int difficulty, double score,
                             String standardAnswer, String analysis, String selectionReason, String knowledge) {
    }

    public List<ExportItem> exportItems(UUID practiceId) {
        return jdbc.query("""
                select q.stem, q.question_type, q.difficulty, pi.score, pi.selection_reason,
                       coalesce(qa.standard_answer, '') as standard_answer,
                       coalesce(qa.analysis, '') as analysis,
                       coalesce((select string_agg(kp.name, '、')
                                 from question_knowledge qk join knowledge_points kp on kp.id = qk.knowledge_point_id
                                 where qk.question_id = q.id), '') as knowledge
                from practice_items pi
                join questions q on q.id = pi.question_id
                left join question_answers qa on qa.question_id = q.id
                where pi.practice_set_id = ? order by pi.sort_order
                """, (rs, rowNum) -> new ExportItem(
                rs.getString("stem"),
                rs.getString("question_type"),
                rs.getInt("difficulty"),
                rs.getBigDecimal("score").doubleValue(),
                rs.getString("standard_answer"),
                rs.getString("analysis"),
                rs.getString("selection_reason"),
                rs.getString("knowledge")
        ), practiceId);
    }

    public String studentName(UUID studentId) {
        return jdbc.query("select display_name from students where id = ?",
                (rs, rowNum) -> rs.getString("display_name"), studentId).stream().findFirst().orElse("");
    }

    public List<MasteryModels.MasteryRow> mastery(UUID studentId) {        return jdbc.query("""
                select knowledge_point_id, mastery_score, confidence, practice_count, correct_count,
                       wrong_count, trend
                from student_mastery where student_id = ? order by mastery_score
                """, (rs, rowNum) -> new MasteryModels.MasteryRow(
                rs.getObject("knowledge_point_id", UUID.class),
                rs.getBigDecimal("mastery_score").doubleValue(),
                rs.getBigDecimal("confidence") == null ? 0 : rs.getBigDecimal("confidence").doubleValue(),
                rs.getInt("practice_count"),
                rs.getInt("correct_count"),
                rs.getInt("wrong_count"),
                rs.getString("trend")
        ), studentId);
    }

    public record Candidate(UUID id, int difficulty, String questionType) {
    }
}
