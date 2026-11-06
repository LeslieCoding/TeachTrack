package com.leslie.teachtrack.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.common.Ids;
import com.leslie.teachtrack.question.QuestionAnswer;
import com.leslie.teachtrack.question.QuestionDetail;
import com.leslie.teachtrack.question.QuestionService;
import com.leslie.teachtrack.sync.SyncChangeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamService {
    private static final Pattern QUESTION_LINE = Pattern.compile("^\\s*(\\d+)\\s*[.、．]\\s*(.+)$");
    private static final Pattern SCORE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*分");

    public record ExamListRow(UUID id, String examName, LocalDate examDate, String status, Double score, Double fullScore) {
    }

    private final ExamRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final SyncChangeRepository sync;
    private final QuestionService questions;

    public ExamService(ExamRepository repository, JdbcTemplate jdbc, ObjectMapper mapper,
                       AuditService audit, SyncChangeRepository sync, QuestionService questions) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.sync = sync;
        this.questions = questions;
    }

    @Transactional
    public ExamPaperRow importPaper(ExamRequests.ImportRequest request, AuthPrincipal principal) {
        UUID studentId = requireUuid(request.studentId(), "STUDENT_REQUIRED", "学生 ID 必填");
        UUID subjectId = requireUuid(request.subjectId(), "SUBJECT_REQUIRED", "学科 ID 必填");
        if (request.examName() == null || request.examName().isBlank()) {
            throw ApiException.badRequest("EXAM_NAME_REQUIRED", "试卷名称必填");
        }
        require("select count(*) from students where id = ? and deleted_at is null", studentId, "学生不存在");
        require("select count(*) from subjects where id = ?", subjectId, "学科不存在");
        UUID id = UUID.randomUUID();
        UUID assetId = request.assetId() == null || request.assetId().isBlank()
                ? null : UUID.fromString(request.assetId());
        repository.insertPaper(id, studentId, subjectId, request.examName().trim(),
                parseDate(request.examDate()), request.fullScore(), request.score(), assetId);
        ExamPaperRow paper = repository.findPaper(id).orElseThrow();
        audit.record(principal, "exam.import", "ExamPaper", id, "SUCCESS", null);
        sync.append("examPaper", id, "UPSERT", 1, null, "{\"id\":\"" + id + "\"}");
        return paper;
    }

    public List<ExamListRow> listByStudent(String studentId, int limit) {
        UUID id = requireUuid(studentId, "STUDENT_REQUIRED", "学生 ID 必填");
        return repository.listByStudent(id, Math.max(1, Math.min(limit, 200))).stream()
                .map(paper -> new ExamListRow(paper.id(), paper.examName(), paper.examDate(),
                        paper.analysisStatus(), paper.score(), paper.fullScore()))
                .toList();
    }

    @Transactional
    public ExamPaperRow.ExamDetail analyze(UUID paperId, ExamRequests.AnalyzeRequest request, AuthPrincipal principal) {
        ExamPaperRow paper = repository.findPaper(paperId)
                .orElseThrow(() -> ApiException.notFound("试卷不存在: " + paperId));
        List<ExamRequests.ParsedQuestionInput> parsed = request.questions();
        if (parsed == null || parsed.isEmpty()) {
            parsed = parseText(request.text());
        }
        if (parsed.isEmpty()) {
            repository.updateStatus(paperId, "FAILED", "没有可分析的题目；请提供 OCR 文本或结构化题目");
            throw ApiException.badRequest("NO_QUESTIONS",
                    "没有可分析的题目；请提供 OCR 文本或结构化题目（不伪造结果）");
        }
        repository.updateStatus(paperId, "PARSING", "");
        repository.clearQuestions(paperId);

        List<String> subjectKnowledge = repository.knowledgeNames(paper.subjectId());
        List<Map<String, Object>> rows = new ArrayList<>();
        double totalScore = 0;
        double totalFull = 0;
        int wrong = 0;
        int lowConfidence = 0;

        int index = 0;
        for (ExamRequests.ParsedQuestionInput input : parsed) {
            String number = input.number() == null || input.number().isBlank()
                    ? String.valueOf(index + 1) : input.number().trim();
            String type = normalizeType(input.type());
            double score = input.score() == null ? 0 : input.score();
            double fullScore = input.fullScore() == null ? 0 : input.fullScore();
            String studentAnswer = nullToEmpty(input.studentAnswer());
            String standardAnswer = nullToEmpty(input.standardAnswer());
            String stem = nullToEmpty(input.stem());

            List<String> knowledgeNames = matchKnowledge(stem, subjectKnowledge);
            String knowledgeSummary = String.join("、", knowledgeNames);
            Instant lastTaught = null;
            boolean wrongBefore = false;
            int repeatCount = 0;
            for (String name : knowledgeNames) {
                Instant taught = repository.lastTaughtAt(paper.studentId(), name);
                if (taught != null && (lastTaught == null || taught.isAfter(lastTaught))) {
                    lastTaught = taught;
                }
                int count = repository.countWrongBefore(paper.studentId(), name);
                if (count > 0) {
                    wrongBefore = true;
                    repeatCount += count;
                }
            }
            boolean correct = fullScore > 0
                    ? score >= fullScore
                    : !studentAnswer.isBlank() && studentAnswer.trim().equalsIgnoreCase(standardAnswer.trim());
            String mistakeType;
            String reason;
            double confidence;
            if (correct) {
                mistakeType = "UNKNOWN";
                reason = "";
                confidence = 0.9;
            } else if (knowledgeNames.isEmpty()) {
                mistakeType = "NEEDS_TEACHER_CONFIRMATION";
                reason = "AI 无法确认错因，需教师确认";
                confidence = 0.0;
            } else {
                mistakeType = "UNKNOWN";
                reason = "待教师确认：该题未得满分";
                confidence = 0.4;
            }
            if (!correct) {
                wrong++;
            }
            if (confidence < 0.5) {
                lowConfidence++;
            }
            double potentialGain = Math.max(0, fullScore - score);
            String suggested = correct ? "保持"
                    : knowledgeNames.isEmpty() ? "教师确认错因后安排训练"
                    : "复习 " + knowledgeSummary + " 并做同类题";

            UUID examQuestionId = repository.insertQuestion(paperId, number, type, score, fullScore,
                    index, null, null, stem, standardAnswer);
            repository.insertAnswer(paper.studentId(), paperId, examQuestionId, studentAnswer, standardAnswer,
                    score, fullScore, correct, confidence);
            repository.insertAnalysis(paperId, examQuestionId, paper.studentId(), abilityOf(type),
                    knowledgeSummary, mistakeType, reason, confidence, null,
                    lastTaught != null, lastTaught, wrongBefore, repeatCount, suggested, potentialGain);

            rows.add(Map.of(
                    "number", number,
                    "type", type,
                    "score", score,
                    "fullScore", fullScore,
                    "correct", correct,
                    "knowledge", knowledgeSummary,
                    "mistakeType", mistakeType,
                    "confidence", confidence
            ));
            totalScore += score;
            totalFull += fullScore;
            index++;
        }

        String summary = reportJson(rows, totalScore, totalFull, wrong, lowConfidence);
        repository.saveReport(paperId, summary,
                aggregateBy(rows, "type"),
                aggregateBy(rows, "knowledge"),
                aggregateBy(rows, "mistakeType"),
                "internal", "deterministic-v1");
        jdbc.update("""
                update exam_papers set score = ?, full_score = ?, analysis_status = 'AI_PARSED',
                    version = version + 1, updated_at = now() where id = ?
                """, totalFull > 0 ? totalScore : paper.score(),
                totalFull > 0 ? totalFull : paper.fullScore(), paperId);
        audit.record(principal, "exam.analyze", "ExamPaper", paperId, "SUCCESS", null);
        sync.append("examPaper", paperId, "UPSERT", paper.version() + 1, null,
                "{\"id\":\"" + paperId + "\",\"status\":\"AI_PARSED\"}");
        return detail(paperId);
    }

    public ExamPaperRow.ExamDetail detail(UUID paperId) {
        ExamPaperRow paper = repository.findPaper(paperId)
                .orElseThrow(() -> ApiException.notFound("试卷不存在: " + paperId));
        return new ExamPaperRow.ExamDetail(paper, repository.listQuestions(paperId),
                repository.listAnswers(paperId), repository.listAnalyses(paperId),
                repository.latestReport(paperId));
    }

    @Transactional
    public ExamPaperRow.ExamDetail updateAnswer(UUID paperId, UUID examQuestionId,
                                                 ExamRequests.UpdateAnswerRequest request,
                                                 AuthPrincipal principal) {
        repository.findPaper(paperId).orElseThrow(() -> ApiException.notFound("试卷不存在"));
        Integer count = jdbc.queryForObject("""
                select count(*) from exam_questions where id = ? and exam_paper_id = ?
                """, Integer.class, examQuestionId, paperId);
        if (count == null || count == 0) {
            throw ApiException.notFound("试卷题目不存在");
        }
        repository.updateAnswer(examQuestionId, request.score(), request.fullScore(),
                request.studentAnswer(), request.teacherComment());
        if (request.mistakeType() != null || request.mistakeReason() != null) {
            repository.updateAnalysis(examQuestionId, request.mistakeType(), request.mistakeReason());
        }
        audit.record(principal, "exam.question.update", "ExamQuestion", examQuestionId, "SUCCESS", null);
        return detail(paperId);
    }

    @Transactional
    public ExamPaperRow.ExamDetail approve(UUID paperId, boolean importToBank, AuthPrincipal principal) {
        ExamPaperRow paper = repository.findPaper(paperId)
                .orElseThrow(() -> ApiException.notFound("试卷不存在: " + paperId));
        if (importToBank) {
            for (ExamPaperRow.ExamQuestionRow row : repository.listQuestions(paperId)) {
                if (row.questionId() != null || row.stem().isBlank()) {
                    continue;
                }
                List<UUID> existing = jdbc.query("""
                        select id from questions where stem_hash = ? and deleted_at is null limit 1
                        """, (rs, rowNum) -> rs.getObject("id", UUID.class), Ids.sha256Hex(row.stem()));
                if (!existing.isEmpty()) {
                    jdbc.update("update exam_questions set question_id = ? where id = ?",
                            existing.get(0), row.id());
                    continue;
                }
                try {
                    QuestionDetail created = questions.create(new QuestionAnswer.CreateRequest(
                            paper.subjectId(), "", "", row.questionType(), 3, row.stem(),
                            "STUDENT_EXAM", paper.examName(), "PERSONAL_USE", "PRIVATE_TEACHING",
                            "NEEDS_TEACHER_REVIEW", null,
                            new QuestionAnswer(row.standardAnswer(), "", "", ""),
                            null, null), principal);
                    jdbc.update("update exam_questions set question_id = ? where id = ?",
                            created.question().id(), row.id());
                } catch (ApiException duplicateOrInvalid) {
                    if (!"DUPLICATE_QUESTION".equals(duplicateOrInvalid.code())) {
                        throw duplicateOrInvalid;
                    }
                    List<UUID> raced = jdbc.query("""
                            select id from questions where stem_hash = ? and deleted_at is null limit 1
                            """, (rs, rowNum) -> rs.getObject("id", UUID.class),
                            Ids.sha256Hex(row.stem()));
                    if (!raced.isEmpty()) {
                        jdbc.update("update exam_questions set question_id = ? where id = ?",
                                raced.get(0), row.id());
                    }
                }
            }
        }
        jdbc.update("""
                update exam_papers set analysis_status = 'APPROVED', version = version + 1,
                    updated_at = now() where id = ?
                """, paperId);
        audit.record(principal, "exam.approve", "ExamPaper", paperId, "SUCCESS", null);
        sync.append("examPaper", paperId, "UPSERT", paper.version() + 1, null,
                "{\"id\":\"" + paperId + "\",\"status\":\"APPROVED\"}");
        return detail(paperId);
    }

    private List<ExamRequests.ParsedQuestionInput> parseText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ExamRequests.ParsedQuestionInput> result = new ArrayList<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = QUESTION_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String stem = matcher.group(2).trim();
            Matcher scoreMatcher = SCORE.matcher(stem);
            double fullScore = scoreMatcher.find() ? Double.parseDouble(scoreMatcher.group(1)) : 0;
            String type = stem.contains("选择") ? "SINGLE_CHOICE"
                    : stem.contains("填空") ? "FILL_BLANK"
                    : stem.contains("实验") ? "EXPERIMENT"
                    : stem.contains("计算") ? "CALCULATION" : "OTHER";
            result.add(new ExamRequests.ParsedQuestionInput(
                    matcher.group(1), type, 0.0, fullScore, "", "", stem));
        }
        return result;
    }

    private String reportJson(List<Map<String, Object>> rows, double totalScore, double totalFull,
                              int wrong, int lowConfidence) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode summary = root.putObject("summary");
        summary.put("totalScore", totalScore);
        summary.put("fullScore", totalFull);
        summary.put("scoreRate", totalFull > 0 ? totalScore / totalFull : 0);
        summary.put("questionCount", rows.size());
        summary.put("wrongCount", wrong);
        summary.put("lowConfidenceCount", lowConfidence);
        return root.toString();
    }

    private String aggregateBy(List<Map<String, Object>> rows, String key) {
        Map<String, double[]> totals = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String value = String.valueOf(row.getOrDefault(key, ""));
            if (value.isBlank()) {
                value = "未匹配";
            }
            double[] values = totals.computeIfAbsent(value, ignored -> new double[3]);
            values[0] += ((Number) row.getOrDefault("score", 0)).doubleValue();
            values[1] += ((Number) row.getOrDefault("fullScore", 0)).doubleValue();
            values[2] += 1;
        }
        ArrayNode array = mapper.createArrayNode();
        totals.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> e.getValue()[1]).reversed())
                .forEach(entry -> {
                    ObjectNode node = array.addObject();
                    node.put("key", entry.getKey());
                    node.put("score", entry.getValue()[0]);
                    node.put("fullScore", entry.getValue()[1]);
                    node.put("questionCount", (int) entry.getValue()[2]);
                    node.put("scoreRate", entry.getValue()[1] > 0 ? entry.getValue()[0] / entry.getValue()[1] : 0);
                });
        return array.toString();
    }

    private static List<String> matchKnowledge(String stem, List<String> names) {
        if (stem.isBlank()) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        names.stream()
                .filter(name -> name != null && name.length() >= 2 && stem.contains(name))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(3)
                .forEach(matches::add);
        return matches;
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "OTHER";
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "SINGLE_CHOICE", "选择题", "单选" -> "SINGLE_CHOICE";
            case "MULTI_CHOICE", "多选题", "多选" -> "MULTI_CHOICE";
            case "FILL_BLANK", "填空题", "填空" -> "FILL_BLANK";
            case "EXPERIMENT", "实验题", "实验探究题" -> "EXPERIMENT";
            case "CALCULATION", "计算题", "解答题" -> "CALCULATION";
            case "SHORT_ANSWER", "简答题" -> "SHORT_ANSWER";
            case "ESSAY", "论述题" -> "ESSAY";
            default -> "OTHER";
        };
    }

    private static String abilityOf(String type) {
        return switch (type) {
            case "EXPERIMENT" -> "实验探究";
            case "CALCULATION" -> "计算与推理";
            case "SINGLE_CHOICE", "MULTI_CHOICE" -> "辨析";
            case "FILL_BLANK" -> "记忆与理解";
            default -> "综合";
        };
    }

    private static UUID requireUuid(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(code, message);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_ID", "ID 格式无效: " + value);
        }
    }

    private void require(String sql, UUID id, String message) {
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        if (count == null || count == 0) {
            throw ApiException.badRequest("NOT_FOUND", message);
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
