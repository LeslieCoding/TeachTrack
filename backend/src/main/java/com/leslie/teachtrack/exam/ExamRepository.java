package com.leslie.teachtrack.exam;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExamRepository {
    private final JdbcTemplate jdbc;

    public ExamRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPaper(UUID id, UUID studentId, UUID subjectId, String examName, LocalDate examDate,
                            Double fullScore, Double score, UUID assetId) {
        jdbc.update("""
                insert into exam_papers (id, student_id, subject_id, exam_name, exam_date, full_score, score,
                    storage_asset_id, analysis_status)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED')
                """, id, studentId, subjectId, examName, examDate, fullScore, score, assetId);
    }

    public Optional<ExamPaperRow> findPaper(UUID id) {
        return jdbc.query("""
                select id, student_id, subject_id, exam_name, exam_date, full_score, score,
                       storage_asset_id, analysis_status, version
                from exam_papers where id = ? and deleted_at is null
                """, (rs, rowNum) -> new ExamPaperRow(
                rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getString("exam_name"),
                rs.getObject("exam_date", LocalDate.class),
                nullableDouble(rs, "full_score"),
                nullableDouble(rs, "score"),
                rs.getObject("storage_asset_id", UUID.class),
                rs.getString("analysis_status"),
                rs.getInt("version")
        ), id).stream().findFirst();
    }

    public List<ExamPaperRow> listByStudent(UUID studentId, int limit) {
        return jdbc.query("""
                select id, student_id, subject_id, exam_name, exam_date, full_score, score,
                       storage_asset_id, analysis_status, version
                from exam_papers where student_id = ? and deleted_at is null
                order by exam_date desc nulls last limit ?
                """, (rs, rowNum) -> new ExamPaperRow(
                rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getString("exam_name"),
                rs.getObject("exam_date", LocalDate.class),
                nullableDouble(rs, "full_score"),
                nullableDouble(rs, "score"),
                rs.getObject("storage_asset_id", UUID.class),
                rs.getString("analysis_status"),
                rs.getInt("version")
        ), studentId, limit);
    }

    public void updateStatus(UUID id, String status, String error) {
        jdbc.update("""
                update exam_papers set analysis_status = ?, parse_error = ?, version = version + 1,
                    updated_at = now() where id = ?
                """, status, error == null ? "" : error, id);
    }

    public void clearQuestions(UUID paperId) {
        jdbc.update("delete from exam_questions where exam_paper_id = ?", paperId);
    }

    public UUID insertQuestion(UUID paperId, String number, String type, double score, double fullScore,
                               int sortOrder, String page, String bboxJson, String stem, String standardAnswer) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into exam_questions (id, exam_paper_id, question_number, question_type, score, full_score,
                    sort_order, source_page, source_bbox, stem, standard_answer)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, id, paperId, number, type, score, fullScore, sortOrder,
                page == null || page.isBlank() ? null : Integer.parseInt(page), bboxJson,
                stem == null ? "" : stem, standardAnswer == null ? "" : standardAnswer);
        return id;
    }

    public UUID insertAnswer(UUID studentId, UUID paperId, UUID examQuestionId, String studentAnswer,
                             String standardAnswer, double score, double fullScore, boolean correct,
                             double confidence) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into student_answers (id, student_id, exam_paper_id, exam_question_id, student_answer,
                    standard_answer, score, full_score, is_correct, confidence)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, studentId, paperId, examQuestionId, studentAnswer, standardAnswer,
                score, fullScore, correct, confidence);
        return id;
    }

    public UUID insertAnalysis(UUID paperId, UUID examQuestionId, UUID studentId, String ability,
                               String knowledgeSummary, String mistakeType, String mistakeReason,
                               double confidence, Integer difficulty, boolean taughtBefore,
                               java.time.Instant lastTaughtAt, boolean wrongBefore, int repeatCount,
                               String suggestedTraining, double potentialScoreGain) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into exam_question_analyses (id, exam_paper_id, exam_question_id, student_id, ability,
                    knowledge_summary, mistake_type, mistake_reason, confidence, difficulty, taught_before,
                    last_taught_at, wrong_before, repeat_count, suggested_training, potential_score_gain,
                    review_status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NEEDS_REVIEW')
                """, id, paperId, examQuestionId, studentId, ability, knowledgeSummary, mistakeType,
                mistakeReason, confidence, difficulty, taughtBefore,
                lastTaughtAt == null ? null : java.sql.Timestamp.from(lastTaughtAt), wrongBefore,
                repeatCount, suggestedTraining, potentialScoreGain);
        return id;
    }

    public List<ExamPaperRow.ExamQuestionRow> listQuestions(UUID paperId) {
        return jdbc.query("""
                select id, exam_paper_id, question_id, question_number, question_type, score, full_score, sort_order,
                       stem, standard_answer
                from exam_questions where exam_paper_id = ? order by sort_order
                """, (rs, rowNum) -> new ExamPaperRow.ExamQuestionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("exam_paper_id", UUID.class),
                rs.getObject("question_id", UUID.class),
                rs.getString("question_number"),
                rs.getString("question_type"),
                rs.getDouble("score"),
                rs.getDouble("full_score"),
                rs.getInt("sort_order"),
                rs.getString("stem"),
                rs.getString("standard_answer")
        ), paperId);
    }

    public List<ExamPaperRow.StudentAnswerRow> listAnswers(UUID paperId) {
        return jdbc.query("""
                select id, exam_question_id, student_answer, standard_answer, score, full_score, is_correct,
                       teacher_comment, ai_comment
                from student_answers where exam_paper_id = ? order by created_at
                """, (rs, rowNum) -> new ExamPaperRow.StudentAnswerRow(
                rs.getObject("id", UUID.class),
                rs.getObject("exam_question_id", UUID.class),
                rs.getString("student_answer"),
                rs.getString("standard_answer"),
                rs.getDouble("score"),
                rs.getDouble("full_score"),
                rs.getObject("is_correct", Boolean.class),
                rs.getString("teacher_comment"),
                rs.getString("ai_comment")
        ), paperId);
    }

    public List<ExamPaperRow.AnalysisRow> listAnalyses(UUID paperId) {
        return jdbc.query("""
                select id, exam_question_id, ability, knowledge_summary, mistake_type, mistake_reason,
                       confidence, difficulty, taught_before, last_taught_at, wrong_before, repeat_count,
                       suggested_training, potential_score_gain, review_status
                from exam_question_analyses where exam_paper_id = ? order by created_at
                """, (rs, rowNum) -> new ExamPaperRow.AnalysisRow(
                rs.getObject("id", UUID.class),
                rs.getObject("exam_question_id", UUID.class),
                rs.getString("ability"),
                rs.getString("knowledge_summary"),
                rs.getString("mistake_type"),
                rs.getString("mistake_reason"),
                nullableDouble(rs, "confidence"),
                rs.getObject("difficulty", Integer.class),
                rs.getBoolean("taught_before"),
                rs.getTimestamp("last_taught_at") == null ? null : rs.getTimestamp("last_taught_at").toInstant(),
                rs.getBoolean("wrong_before"),
                rs.getInt("repeat_count"),
                rs.getString("suggested_training"),
                rs.getDouble("potential_score_gain"),
                rs.getString("review_status")
        ), paperId);
    }

    public void updateAnswer(UUID examQuestionId, Double score, Double fullScore, String studentAnswer,
                             String teacherComment) {
        jdbc.update("""
                update student_answers set score = coalesce(?::numeric, score), full_score = coalesce(?::numeric, full_score),
                    student_answer = coalesce(?, student_answer), teacher_comment = coalesce(?, teacher_comment)
                where exam_question_id = ?
                """, score, fullScore, studentAnswer, teacherComment, examQuestionId);
        jdbc.update("""
                update exam_questions set score = coalesce(?::numeric, score), full_score = coalesce(?::numeric, full_score)
                where id = ?
                """, score, fullScore, examQuestionId);
    }

    public void updateAnalysis(UUID examQuestionId, String mistakeType, String mistakeReason) {
        jdbc.update("""
                update exam_question_analyses set mistake_type = coalesce(?, mistake_type),
                    mistake_reason = coalesce(?, mistake_reason), review_status = 'APPROVED', updated_at = now()
                where exam_question_id = ?
                """, mistakeType, mistakeReason, examQuestionId);
    }

    public void saveReport(UUID paperId, String summaryJson, String byTypeJson, String byKnowledgeJson,
                           String byMistakeJson, String provider, String model) {
        jdbc.update("""
                insert into exam_analysis_reports (id, exam_paper_id, summary, by_question_type, by_knowledge,
                    by_mistake_type, ai_provider, ai_model, review_status)
                values (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, 'DRAFT')
                """, UUID.randomUUID(), paperId, summaryJson, byTypeJson, byKnowledgeJson, byMistakeJson,
                provider, model);
    }

    public String latestReport(UUID paperId) {
        return jdbc.query("""
                select jsonb_build_object(
                    'summary', summary,
                    'byQuestionType', by_question_type,
                    'byKnowledge', by_knowledge,
                    'byMistakeType', by_mistake_type,
                    'generatedAt', generated_at,
                    'reviewStatus', review_status
                )::text
                from exam_analysis_reports where exam_paper_id = ? order by generated_at desc limit 1
                """, (rs, rowNum) -> rs.getString(1), paperId).stream().findFirst().orElse("{}");
    }

    public List<String> knowledgeNames(UUID subjectId) {
        return jdbc.query("select name from knowledge_points where subject_id = ? and deleted_at is null",
                (rs, rowNum) -> rs.getString("name"), subjectId);
    }

    public java.time.Instant lastTaughtAt(UUID studentId, String knowledgeName) {
        LocalDate date = jdbc.query("""
                select max(date) from course_sessions
                where student_id = ? and deleted_at is null
                  and (lesson_title ilike ? or lesson_content ilike ? or problems ilike ?)
                """, rs -> rs.next() ? rs.getObject(1, LocalDate.class) : null,
                studentId, "%" + knowledgeName + "%", "%" + knowledgeName + "%", "%" + knowledgeName + "%");
        return date == null ? null : date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
    }

    public int countWrongBefore(UUID studentId, String knowledgeName) {
        Integer value = jdbc.queryForObject("""
                select count(*) from mistake_records
                where student_id = ? and coalesce(mistake_reason,'') ilike ?
                """, Integer.class, studentId, "%" + knowledgeName + "%");
        return value == null ? 0 : value;
    }

    private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.math.BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
