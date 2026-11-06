package com.leslie.teachtrack.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leslie.teachtrack.common.Ids;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Idempotent migration for the three supported sources:
 * TeachTrack Historical Seed v1, TeachTrack Backup JSON and .ttseed.json.
 *
 * Every row keeps its original id in legacy_id; the UUID is derived with UUIDv5
 * so re-running the same file never creates a second student or session.
 */
@Service
public class MigrationService {
    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);
    private static final DateTimeFormatter SLASH_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");

    public record Issue(String severity, String entityType, String sourceRef, String message) {
    }

    public record Result(
            UUID batchId,
            String format,
            String sourceName,
            String sourceSha256,
            boolean dryRun,
            int students,
            int courses,
            int scores,
            int knowledgePoints,
            int communications,
            int problems,
            int questions,
            int issues
    ) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public MigrationService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Result migrate(Path source, String formatHint, boolean dryRun, String actor) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(source);
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取迁移文件: " + source, exception);
        }
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (IOException exception) {
            throw new IllegalArgumentException("不是合法 JSON: " + source, exception);
        }
        String format = detectFormat(root, formatHint);
        String sha = Ids.sha256Hex(json);

        ImportContext context = new ImportContext(UUID.randomUUID(), source.getFileName().toString(),
                format, sha, dryRun);
        try {
            return transaction.execute(status -> {
                insertJob(context);
                dispatch(root, context);
                Result result = result(context, actor);
                finishJob(context, result);
                if (dryRun) {
                    status.setRollbackOnly();
                }
                return result;
            });
        } catch (RuntimeException exception) {
            recordFailedJob(context, exception);
            throw exception;
        }
    }

    private void dispatch(JsonNode root, ImportContext context) {
        switch (context.format) {
            case "SEED_V1" -> importSeedV1(root, context);
            case "BACKUP_JSON" -> importBackup(root, context);
            case "TTSEED" -> importTtseed(root, context);
            default -> throw new IllegalArgumentException("不支持的迁移格式: " + context.format);
        }
    }

    private static String detectFormat(JsonNode root, String hint) {
        if (hint != null && !hint.isBlank()) {
            return hint.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        }
        String format = root.path("format").asText("").toLowerCase(Locale.ROOT);
        if (format.contains("historical seed")) {
            return "SEED_V1";
        }
        if (format.contains("teachtrack backup")) {
            return "BACKUP_JSON";
        }
        if (format.contains("ttseed")) {
            return "TTSEED";
        }
        JsonNode first = root.path("students").isArray() && root.path("students").size() > 0
                ? root.path("students").get(0) : null;
        if (first != null && (first.has("courseSessions") || first.has("alias") || first.has("academicProfile"))) {
            return "TTSEED";
        }
        throw new IllegalArgumentException("无法识别迁移文件格式，请通过 format 参数指定 SEED_V1/BACKUP_JSON/TTSEED");
    }

    // ---------------------------------------------------------------- seed v1

    private void importSeedV1(JsonNode root, ImportContext context) {
        for (JsonNode item : array(root, "students")) {
            String legacyStudentId = text(item, "id", null);
            if (legacyStudentId == null) {
                issue(context, "WARN", "student", "?", "缺少 id，已跳过");
                continue;
            }
            String subjectName = text(item, "subject", "");
            UUID subjectId = subjectId(context, subjectName);
            String fullName = text(item, "fullName", null);
            String displayName = text(item, "displayName", fullName == null ? "未命名-" + legacyStudentId : fullName);
            UUID studentId = upsertStudent(context, legacyStudentId, fullName, displayName,
                    text(item, "gender", null), text(item, "historicalGrade", ""), null,
                    text(item, "status", "UNKNOWN"), text(item, "nameCompleteness",
                            fullName == null ? "PARTIAL" : "CONFIRMED"),
                    "", "", subjectId, "", null);

            com.fasterxml.jackson.databind.node.ObjectNode profile = mapper.createObjectNode();
            copyIfPresent(item, profile, "strengths");
            copyIfPresent(item, profile, "weaknesses");
            copyIfPresent(item, profile, "focus");
            upsertProfile(context, studentId, profile.toString(), item.path("weaknesses").toString(),
                    item.path("focus").toString());

            List<String> weaknessNames = strings(item, "weaknesses");
            int knowledgeIndex = 0;
            for (String raw : strings(item, "knowledge")) {
                String[] pieces = raw.split("/", 2);
                String group = pieces.length > 1 ? pieces[0] : "";
                String name = pieces.length > 1 ? pieces[1] : pieces[0];
                UUID parentId = null;
                if (!group.isBlank()) {
                    String groupLegacy = "seed-group-" + Ids.v5("knowledge-group", subjectName + "|" + group);
                    parentId = upsertKnowledge(context, groupLegacy, null, subjectId,
                            text(item, "historicalGrade", ""), group, "KG-" + Ids.sha256Hex(subjectName + "|" + group).substring(0, 10),
                            "历史 Seed 自动建立的知识分类", knowledgeIndex);
                }
                String key = subjectName + "|" + group + "|" + name;
                String legacy = "seed-kp-" + Ids.v5("knowledge", key);
                UUID knowledgeId = upsertKnowledge(context, legacy, parentId, subjectId,
                        text(item, "historicalGrade", ""), name,
                        "KP-" + Ids.sha256Hex(key).substring(0, 12), "", knowledgeIndex);
                boolean weak = weaknessNames.stream().anyMatch(w -> w.contains(name) || name.contains(w));
                upsertLegacyProgress(context, studentId, knowledgeId, weak ? 2 : 1, "来自历史 Seed；不是 AI 推断");
                knowledgeIndex++;
            }

            int courseIndex = 0;
            for (JsonNode course : array(item, "courses")) {
                String legacyCourse = "seed-course-" + legacyStudentId + "-" + courseIndex;
                upsertCourse(context, legacyCourse, studentId, text(course, "recordDate", ""),
                        "", "", subjectName, text(course, "title", ""),
                        text(course, "content", ""), text(course, "performance", ""),
                        text(course, "problems", ""), "", text(course, "nextLessonPlan", ""),
                        "历史教学资料；日期精度：" + text(course, "dateAccuracy", "UNKNOWN"));
                courseIndex++;
            }

            int scoreIndex = 0;
            for (JsonNode score : array(item, "scores")) {
                String legacyScore = "seed-score-" + legacyStudentId + "-" + scoreIndex;
                upsertScore(context, legacyScore, studentId, null,
                        text(score, "type", "历史成绩"), text(score, "exactDate", ""), subjectId,
                        doubleValue(score, "score", 0), doubleValue(score, "fullScore", 0),
                        text(score, "scoreText", ""), text(score, "fullScoreText", ""),
                        text(score, "scoreAccuracy", "UNKNOWN"), text(score, "note", ""));
                scoreIndex++;
            }

            int communicationIndex = 0;
            for (JsonNode communication : array(item, "communications")) {
                String legacy = "seed-communication-" + legacyStudentId + "-" + communicationIndex;
                upsertCommunication(context, legacy, studentId, text(communication, "date", ""),
                        text(communication, "contact", ""), text(communication, "method", "其他"),
                        text(communication, "summary", ""), text(communication, "parentFeedback", ""),
                        text(communication, "followUp", ""));
                communicationIndex++;
            }

            int problemIndex = 0;
            for (String weakness : weaknessNames) {
                upsertProblem(context, "seed-problem-" + legacyStudentId + "-" + problemIndex,
                        studentId, weakness, weakness, "", "OPEN");
                problemIndex++;
            }

            int progressIndex = 0;
            for (JsonNode record : array(item, "progress")) {
                upsertStudentProgress(context, "seed-progress-" + legacyStudentId + "-" + progressIndex,
                        studentId, text(record, "date", ""), text(record, "dateAccuracy", "UNKNOWN"),
                        text(record, "category", ""), text(record, "title", ""),
                        text(record, "detail", ""), "历史教学资料");
                progressIndex++;
            }
        }

        for (JsonNode item : array(root, "unresolvedHistoricalRecords")) {
            upsertUnresolved(context, text(item, "id", null), text(item, "recordDate", ""),
                    text(item, "dateAccuracy", "UNKNOWN"), text(item, "title", ""),
                    text(item, "content", ""), "匿名历史教学资料");
        }
    }

    // ---------------------------------------------------------------- backup JSON

    private void importBackup(JsonNode root, ImportContext context) {
        for (JsonNode item : array(root, "students")) {
            String legacy = text(item, "id", null);
            if (legacy == null) {
                continue;
            }
            UUID subjectId = subjectId(context, text(item, "subject", ""));
            upsertStudent(context, legacy, text(item, "name", null),
                    text(item, "displayName", text(item, "name", "未命名")),
                    text(item, "gender", null), text(item, "grade", ""),
                    text(item, "currentGrade", null), text(item, "status", "ACTIVE"),
                    text(item, "nameCompleteness", "CONFIRMED"), text(item, "school", ""),
                    text(item, "className", ""), subjectId, text(item, "notes", ""),
                    text(item, "startDate", null));
        }

        Map<String, JsonNode> exams = new HashMap<>();
        for (JsonNode item : array(root, "exams")) {
            exams.put(text(item, "id", ""), item);
        }

        for (JsonNode item : array(root, "knowledgePoints")) {
            String legacy = text(item, "id", null);
            if (legacy == null) {
                continue;
            }
            UUID subjectId = subjectId(context, text(item, "subject", ""));
            String parentLegacy = text(item, "parentId", null);
            UUID parentId = parentLegacy == null ? null : Ids.fromLegacy("knowledge", parentLegacy);
            upsertKnowledge(context, legacy, parentId, subjectId, text(item, "grade", ""),
                    text(item, "name", "未命名知识点"), text(item, "code", null),
                    text(item, "description", ""), intValue(item, "orderIndex", 0));
        }

        for (JsonNode item : array(root, "knowledgeProgress")) {
            String studentLegacy = text(item, "studentId", null);
            String knowledgeLegacy = text(item, "knowledgePointId", null);
            if (studentLegacy == null || knowledgeLegacy == null) {
                continue;
            }
            upsertLegacyProgress(context, studentIdOf(context, studentLegacy),
                    Ids.fromLegacy("knowledge", knowledgeLegacy), intValue(item, "level", 0),
                    text(item, "notes", ""));
        }

        for (JsonNode item : array(root, "scores")) {
            String legacy = text(item, "id", null);
            String studentLegacy = text(item, "studentId", null);
            if (legacy == null || studentLegacy == null) {
                continue;
            }
            JsonNode exam = exams.get(text(item, "examId", ""));
            upsertScore(context, legacy, studentIdOf(context, studentLegacy),
                    text(item, "examId", null),
                    exam == null ? "历史成绩" : text(exam, "name", "历史成绩"),
                    exam == null ? "" : text(exam, "date", ""),
                    subjectId(context, exam == null ? "" : text(exam, "subject", "")),
                    doubleValue(item, "score", 0), doubleValue(item, "fullScore", 0),
                    text(item, "scoreLabel", ""), text(item, "fullScoreLabel", ""),
                    text(item, "scoreAccuracy", "EXACT"), text(item, "notes", ""));
        }

        for (JsonNode item : array(root, "scoreItems")) {
            String legacy = text(item, "id", null);
            String scoreLegacy = text(item, "scoreId", null);
            if (legacy == null || scoreLegacy == null) {
                continue;
            }
            jdbc.update("""
                    insert into score_items (id, legacy_id, score_id, category, lost_score, knowledge_point_id, notes)
                    values (?, ?, (select id from student_scores where legacy_id = ?), ?, ?, ?, ?)
                    on conflict (legacy_id) do update set category = excluded.category,
                        lost_score = excluded.lost_score, knowledge_point_id = excluded.knowledge_point_id,
                        notes = excluded.notes
                    """, Ids.fromLegacy("score-item", legacy), legacy, scoreLegacy,
                    text(item, "category", ""), doubleValue(item, "lostScore", 0),
                    nullableUuid(text(item, "knowledgePointId", null), "knowledge"),
                    text(item, "notes", ""));
        }

        for (JsonNode item : array(root, "courses")) {
            String legacy = text(item, "id", null);
            String studentLegacy = text(item, "studentId", null);
            if (legacy == null || studentLegacy == null) {
                continue;
            }
            upsertCourse(context, legacy, studentIdOf(context, studentLegacy), text(item, "date", ""),
                    text(item, "startTime", ""), text(item, "endTime", ""), text(item, "subject", ""),
                    text(item, "title", ""), text(item, "content", ""), text(item, "performance", ""),
                    text(item, "problems", ""), text(item, "homeworkSummary", ""),
                    text(item, "nextLessonPlan", ""), text(item, "teacherNotes", ""));
        }

        for (JsonNode item : array(root, "homework")) {
            upsertHomework(context, text(item, "id", null), text(item, "studentId", null),
                    text(item, "courseId", null), text(item, "name", "作业"),
                    text(item, "assignedDate", ""), text(item, "dueDate", null),
                    text(item, "content", ""), text(item, "status", "未完成"),
                    text(item, "teacherComment", ""));
        }

        for (JsonNode item : array(root, "communications")) {
            upsertCommunication(context, text(item, "id", null), idOf(context, item, "studentId"),
                    text(item, "date", ""), text(item, "contact", ""), text(item, "method", "微信"),
                    text(item, "summary", ""), text(item, "parentFeedback", ""), text(item, "followUp", ""));
        }

        for (JsonNode item : array(root, "lessonPlans")) {
            upsertLessonPlan(context, text(item, "id", null), idOf(context, item, "studentId"),
                    text(item, "plannedDate", ""), text(item, "chapter", ""),
                    text(item, "knowledgePoints", ""), text(item, "focus", ""),
                    text(item, "difficulty", ""), text(item, "review", ""),
                    text(item, "newContent", ""), text(item, "homework", ""), text(item, "notes", ""));
        }

        for (JsonNode item : array(root, "tasks")) {
            upsertTask(context, text(item, "id", null), text(item, "title", "任务"),
                    text(item, "studentId", null), text(item, "type", "其他"),
                    text(item, "dueDate", null), text(item, "priority", "普通"),
                    text(item, "status", "待处理"), text(item, "notes", ""));
        }

        for (JsonNode item : array(root, "parentContacts")) {
            upsertParentContact(context, text(item, "id", null), text(item, "studentId", null),
                    text(item, "name", ""), text(item, "phone", ""), text(item, "relation", ""),
                    boolValue(item, "isPrimary", false), text(item, "notes", ""));
        }

        for (JsonNode item : array(root, "problemRecords")) {
            String legacy = text(item, "id", null);
            if (legacy == null) {
                continue;
            }
            upsertProblem(context, legacy, idOf(context, item, "studentId"),
                    text(item, "title", ""), text(item, "description", ""),
                    text(item, "date", ""), mapProblemStatus(text(item, "status", "待复习")));
        }

        for (JsonNode item : array(root, "templates")) {
            String legacy = text(item, "id", null);
            if (legacy == null) {
                continue;
            }
            jdbc.update("""
                    insert into templates (id, legacy_id, name, type, content, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (legacy_id) do update set name = excluded.name, type = excluded.type,
                        content = excluded.content, updated_at = now()
                    """, Ids.fromLegacy("template", legacy), legacy, text(item, "name", ""),
                    text(item, "type", "课后反馈"), text(item, "content", ""),
                    timestamp(item, "createdAt"), timestamp(item, "updatedAt"));
        }

        for (JsonNode item : array(root, "tags")) {
            String legacy = text(item, "id", null);
            if (legacy == null) {
                continue;
            }
            jdbc.update("""
                    insert into tags (id, legacy_id, name, color) values (?, ?, ?, ?)
                    on conflict (legacy_id) do update set name = excluded.name, color = excluded.color
                    """, Ids.fromLegacy("tag", legacy), legacy, text(item, "name", ""),
                    text(item, "color", "#176B57"));
        }

        for (JsonNode item : array(root, "studentTags")) {
            String studentLegacy = text(item, "studentId", null);
            String tagLegacy = text(item, "tagId", null);
            if (studentLegacy == null || tagLegacy == null) {
                continue;
            }
            jdbc.update("""
                    insert into student_tags (student_id, tag_id)
                    values (?, (select id from tags where legacy_id = ?))
                    on conflict do nothing
                    """, studentIdOf(context, studentLegacy), tagLegacy);
        }

        for (JsonNode item : array(root, "attachments")) {
            String legacy = text(item, "id", null);
            if (legacy == null) {
                continue;
            }
            jdbc.update("""
                    insert into assets (id, owner_type, owner_id, storage_provider, storage_key, mime_type,
                        size_bytes, sha256, visibility, status)
                    values (?, 'LEGACY_ATTACHMENT', null, 'local', ?, ?, ?, ?, 'PRIVATE', 'PENDING')
                    on conflict (storage_provider, storage_key) do nothing
                    """, Ids.fromLegacy("asset", legacy), "legacy/" + legacy,
                    text(item, "mimeType", "application/octet-stream"), longValue(item, "size", 0),
                    text(item, "sha256", ""));
        }

        for (JsonNode item : array(root, "historicalLearningRecords")) {
            upsertHistorical(context, text(item, "id", null), text(item, "studentId", null),
                    text(item, "recordDate", ""), text(item, "dateAccuracy", "UNKNOWN"),
                    text(item, "subject", ""), text(item, "title", ""),
                    text(item, "content", ""), text(item, "source", "历史教学资料"));
        }

        for (JsonNode item : array(root, "studentProgress")) {
            upsertStudentProgress(context, text(item, "id", null), idOf(context, item, "studentId"),
                    text(item, "date", ""), text(item, "dateAccuracy", "UNKNOWN"),
                    text(item, "category", ""), text(item, "title", ""),
                    text(item, "detail", ""), text(item, "source", "历史教学资料"));
        }

        for (JsonNode item : array(root, "unresolvedHistoricalRecords")) {
            upsertUnresolved(context, text(item, "id", null), text(item, "recordDate", ""),
                    text(item, "dateAccuracy", "UNKNOWN"), text(item, "title", ""),
                    text(item, "content", ""), text(item, "source", "历史教学资料"));
        }
    }

    // ---------------------------------------------------------------- ttseed

    private void importTtseed(JsonNode root, ImportContext context) {
        for (JsonNode item : array(root, "students")) {
            String legacy = text(item, "id", null);
            if (legacy == null) {
                legacy = "ttseed-student-" + Ids.sha256Hex(text(item, "displayName", "") + ":" + text(item, "subject", "")).substring(0, 16);
            }
            String displayName = text(item, "displayName", text(item, "fullName", "未命名"));
            UUID subjectId = subjectId(context, text(item, "subject", ""));
            UUID studentId = upsertStudent(context, legacy, text(item, "fullName", null), displayName,
                    text(item, "gender", null), text(item, "grade", ""), text(item, "currentGrade", null),
                    text(item, "status", "UNKNOWN"), text(item, "nameCompleteness",
                            text(item, "fullName", null) == null ? "PARTIAL" : "CONFIRMED"),
                    text(item, "school", ""), text(item, "className", ""), subjectId,
                    text(item, "notes", ""), text(item, "startDate", null));

            for (String alias : strings(item, "alias")) {
                if (!alias.isBlank()) {
                    jdbc.update("""
                            insert into student_aliases (id, student_id, alias, alias_type, source)
                            values (?, ?, ?, 'NICKNAME', 'ttseed')
                            on conflict (student_id, alias) do nothing
                            """, UUID.randomUUID(), studentId, alias.trim());
                }
            }

            com.fasterxml.jackson.databind.node.ObjectNode profile = mapper.createObjectNode();
            copyIfPresent(item, profile, "academicProfile");
            copyIfPresent(item, profile, "learningCharacteristics");
            copyIfPresent(item, profile, "strengths");
            copyIfPresent(item, profile, "weaknesses");
            copyIfPresent(item, profile, "riskFlags");
            copyIfPresent(item, profile, "specialRules");
            upsertProfile(context, studentId, profile.toString(), item.path("weaknesses").toString(),
                    item.path("specialRules").toString());

            JsonNode parentContact = item.path("parentContact");
            if (parentContact.isObject()) {
                upsertParentContact(context, null, legacy, text(parentContact, "name", ""),
                        text(parentContact, "phone", ""), text(parentContact, "relation", ""),
                        boolValue(parentContact, "isPrimary", true), text(parentContact, "notes", ""));
            } else if (parentContact.isTextual() && !parentContact.asText().isBlank()) {
                upsertParentContact(context, null, legacy, parentContact.asText(), "", "", true, "");
            }

            int kpIndex = 0;
            for (JsonNode progress : array(item, "knowledgeProgress")) {
                String name = text(progress, "name", text(progress, "knowledgePointName", ""));
                String explicitId = text(progress, "knowledgePointId", null);
                UUID knowledgeId;
                if (explicitId != null) {
                    knowledgeId = Ids.fromLegacy("knowledge", explicitId);
                } else if (!name.isBlank()) {
                    String key = text(item, "subject", "") + "|" + name;
                    knowledgeId = upsertKnowledge(context, "ttseed-kp-" + Ids.v5("knowledge", key),
                            null, subjectId, text(item, "grade", ""), name,
                            "KP-" + Ids.sha256Hex(key).substring(0, 12), "", kpIndex);
                } else {
                    issue(context, "WARN", "knowledgeProgress", legacy, "缺少 name/knowledgePointId，已跳过");
                    continue;
                }
                upsertLegacyProgress(context, studentId, knowledgeId,
                        intValue(progress, "level", 0), text(progress, "notes", ""));
                kpIndex++;
            }

            int scoreIndex = 0;
            for (JsonNode score : array(item, "scores")) {
                upsertScore(context, "ttseed-score-" + legacy + "-" + scoreIndex, studentId,
                        null, text(score, "examName", text(score, "name", "历史成绩")),
                        text(score, "date", ""), subjectId,
                        doubleValue(score, "score", 0), doubleValue(score, "fullScore", 0),
                        text(score, "scoreLabel", ""), text(score, "fullScoreLabel", ""),
                        text(score, "scoreAccuracy", "EXACT"), text(score, "notes", ""));
                scoreIndex++;
            }

            int planIndex = 0;
            for (JsonNode plan : array(item, "coursePlans")) {
                upsertLessonPlan(context, "ttseed-plan-" + legacy + "-" + planIndex, studentId,
                        text(plan, "plannedDate", text(plan, "date", "")), text(plan, "chapter", ""),
                        text(plan, "knowledgePoints", ""), text(plan, "focus", ""),
                        text(plan, "difficulty", ""), text(plan, "review", ""),
                        text(plan, "newContent", ""), text(plan, "homework", ""), text(plan, "notes", ""));
                planIndex++;
            }

            int communicationIndex = 0;
            for (JsonNode communication : array(item, "communications")) {
                upsertCommunication(context, "ttseed-communication-" + legacy + "-" + communicationIndex,
                        studentId, text(communication, "date", ""), text(communication, "contact", ""),
                        text(communication, "method", "微信"), text(communication, "summary", ""),
                        text(communication, "parentFeedback", ""), text(communication, "followUp", ""));
                communicationIndex++;
            }

            int sessionIndex = 0;
            for (JsonNode session : array(item, "courseSessions")) {
                String sessionLegacy = text(session, "id", "ttseed-course-" + legacy + "-" + sessionIndex);
                upsertCourse(context, sessionLegacy, studentId, text(session, "date", ""),
                        text(session, "startTime", ""), text(session, "endTime", ""),
                        text(session, "subject", text(item, "subject", "")),
                        text(session, "lessonTitle", text(session, "title", "")),
                        text(session, "lessonContent", text(session, "content", "")),
                        text(session, "performance", ""), text(session, "problems", ""),
                        text(session, "homeworkSummary", ""), text(session, "nextLessonPlan", ""),
                        text(session, "teacherNotes", ""));
                sessionIndex++;
            }

            int problemIndex = 0;
            for (String weakness : strings(item, "weaknesses")) {
                upsertProblem(context, "ttseed-problem-" + legacy + "-" + problemIndex,
                        legacy, weakness, weakness, "", "OPEN");
                problemIndex++;
            }
        }

        for (JsonNode session : array(root, "courseSessions")) {
            String studentLegacy = text(session, "studentId", null);
            if (studentLegacy == null) {
                issue(context, "WARN", "courseSession", "?", "顶层 courseSession 缺少 studentId，已跳过");
                continue;
            }
            String sessionLegacy = text(session, "id", "ttseed-course-" + studentLegacy + "-" + Ids.sha256Hex(session.toString()).substring(0, 12));
            upsertCourse(context, sessionLegacy, studentIdOf(context, studentLegacy),
                    text(session, "date", ""), text(session, "startTime", ""), text(session, "endTime", ""),
                    text(session, "subject", ""), text(session, "lessonTitle", text(session, "title", "")),
                    text(session, "lessonContent", text(session, "content", "")),
                    text(session, "performance", ""), text(session, "problems", ""),
                    text(session, "homeworkSummary", ""), text(session, "nextLessonPlan", ""),
                    text(session, "teacherNotes", ""));
        }
    }

    // ---------------------------------------------------------------- upserts

    private UUID upsertStudent(ImportContext context, String legacy, String fullName, String displayName,
                               String gender, String gradeAtRecord, String currentGrade, String status,
                               String nameCompleteness, String school, String className, UUID subjectId,
                               String notes, String startDate) {
        UUID id = Ids.fromLegacy("student", legacy);
        jdbc.update("""
                insert into students (id, legacy_id, full_name, display_name, status, gender, current_grade,
                    grade_at_record, name_completeness, school, class_name, subject_id, notes, start_date,
                    import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set
                    full_name = excluded.full_name, display_name = excluded.display_name,
                    status = excluded.status, gender = excluded.gender,
                    current_grade = coalesce(excluded.current_grade, students.current_grade),
                    grade_at_record = coalesce(excluded.grade_at_record, students.grade_at_record),
                    name_completeness = excluded.name_completeness, school = excluded.school,
                    class_name = excluded.class_name, subject_id = excluded.subject_id,
                    notes = excluded.notes, start_date = coalesce(excluded.start_date, students.start_date),
                    import_batch_id = excluded.import_batch_id, version = students.version + 1,
                    updated_at = now()
                """, id, legacy, fullName, displayName, safeStatus(status), gender, currentGrade,
                blankToNull(gradeAtRecord), nameCompleteness, school, className, subjectId, notes,
                parseDate(startDate), context.batchId);
        context.studentIds.put(legacy, id);
        context.students++;
        return id;
    }

    private UUID upsertKnowledge(ImportContext context, String legacy, UUID parentId, UUID subjectId,
                                 String grade, String name, String code, String description, int sortOrder) {
        UUID id = Ids.fromLegacy("knowledge", legacy);
        String safeCode = code == null || code.isBlank() ? "KP-" + Ids.sha256Hex(legacy).substring(0, 12) : code;
        jdbc.update("""
                insert into knowledge_points (id, legacy_id, parent_id, subject_id, grade, name, code,
                    description, sort_order, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set parent_id = excluded.parent_id,
                    subject_id = excluded.subject_id, grade = excluded.grade, name = excluded.name,
                    code = excluded.code, description = excluded.description, sort_order = excluded.sort_order,
                    import_batch_id = excluded.import_batch_id, updated_at = now()
                """, id, legacy, parentId, subjectId, blankToNull(grade), name, safeCode,
                description, sortOrder, context.batchId);
        context.knowledgeIds.put(legacy, id);
        return id;
    }

    private void upsertProfile(ImportContext context, UUID studentId, String academicProfile,
                               String weaknessesJson, String specialRulesJson) {
        jdbc.update("""
                insert into student_profiles (student_id, academic_profile, weaknesses, special_rules)
                values (?, ?::jsonb, ?::text[], ?::text[])
                on conflict (student_id) do update set academic_profile = excluded.academic_profile,
                    weaknesses = excluded.weaknesses, special_rules = excluded.special_rules,
                    updated_at = now()
                """, studentId, academicProfile, textArray(weaknessesJson), textArray(specialRulesJson));
    }

    private void upsertLegacyProgress(ImportContext context, UUID studentId, UUID knowledgeId, int level, String notes) {
        jdbc.update("""
                insert into student_knowledge_progress_legacy (student_id, knowledge_point_id, level, last_updated, notes)
                values (?, ?, ?, now(), ?)
                on conflict (student_id, knowledge_point_id) do update set level = excluded.level,
                    last_updated = now(), notes = excluded.notes
                """, studentId, knowledgeId, level, notes);
        jdbc.update("""
                insert into student_mastery (student_id, knowledge_point_id, mastery_score, confidence,
                    practice_count, formula_version)
                values (?, ?, ?, 0, 0, 'legacy-import')
                on conflict (student_id, knowledge_point_id) do nothing
                """, studentId, knowledgeId, Math.min(100, Math.max(0, level * 25)));
    }

    private void upsertCourse(ImportContext context, String legacy, UUID studentId, String date,
                              String startTime, String endTime, String subject, String title, String content,
                              String performance, String problems, String homeworkSummary, String nextLessonPlan,
                              String teacherNotes) {
        UUID id = Ids.fromLegacy("course", legacy);
        LocalDate parsed = parseDate(date);
        if (parsed == null) {
            issue(context, "WARN", "courseSession", legacy, "日期无法解析，date 置空: " + date);
        }
        jdbc.update("""
                insert into course_sessions (id, legacy_id, student_id, date, start_time, end_time, subject_id,
                    lesson_title, lesson_content, performance, problems, homework_summary, next_lesson_plan,
                    teacher_notes, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id, date = excluded.date,
                    start_time = excluded.start_time, end_time = excluded.end_time, subject_id = excluded.subject_id,
                    lesson_title = excluded.lesson_title, lesson_content = excluded.lesson_content,
                    performance = excluded.performance, problems = excluded.problems,
                    homework_summary = excluded.homework_summary, next_lesson_plan = excluded.next_lesson_plan,
                    teacher_notes = excluded.teacher_notes, import_batch_id = excluded.import_batch_id,
                    version = course_sessions.version + 1, updated_at = now()
                """, id, legacy, studentId, parsed, nullToEmpty(startTime), nullToEmpty(endTime),
                subjectId(context, subject), nullToEmpty(title), nullToEmpty(content),
                nullToEmpty(performance), nullToEmpty(problems), nullToEmpty(homeworkSummary),
                nullToEmpty(nextLessonPlan), nullToEmpty(teacherNotes), context.batchId);
        context.courses++;
    }

    private void upsertScore(ImportContext context, String legacy, UUID studentId, String examLegacy,
                             String examName, String examDate, UUID subjectId, double score, double fullScore,
                             String scoreLabel, String fullScoreLabel, String accuracy, String notes) {
        UUID id = Ids.fromLegacy("score", legacy);
        jdbc.update("""
                insert into student_scores (id, legacy_id, student_id, exam_id, exam_name, exam_date,
                    subject_id, score, full_score, score_label, full_score_label, score_accuracy, notes,
                    import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id, exam_id = excluded.exam_id,
                    exam_name = excluded.exam_name, exam_date = excluded.exam_date, subject_id = excluded.subject_id,
                    score = excluded.score, full_score = excluded.full_score, score_label = excluded.score_label,
                    full_score_label = excluded.full_score_label, score_accuracy = excluded.score_accuracy,
                    notes = excluded.notes, import_batch_id = excluded.import_batch_id, updated_at = now()
                """, id, legacy, studentId, nullableUuid(examLegacy, "exam"), nullToEmpty(examName),
                parseDate(examDate), subjectId, score, fullScore, nullToEmpty(scoreLabel),
                nullToEmpty(fullScoreLabel), nullToEmpty(accuracy), nullToEmpty(notes), context.batchId);
        context.scores++;
    }

    private void upsertCommunication(ImportContext context, String legacy, UUID studentId, String date,
                                     String contact, String method, String summary, String parentFeedback,
                                     String followUp) {
        if (legacy == null || studentId == null) {
            return;
        }
        jdbc.update("""
                insert into communications (id, legacy_id, student_id, date, contact, method, summary,
                    parent_feedback, follow_up, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id, date = excluded.date,
                    contact = excluded.contact, method = excluded.method, summary = excluded.summary,
                    parent_feedback = excluded.parent_feedback, follow_up = excluded.follow_up,
                    import_batch_id = excluded.import_batch_id, updated_at = now()
                """, Ids.fromLegacy("communication", legacy), legacy, studentId, parseDate(date),
                nullToEmpty(contact), nullToEmpty(method), nullToEmpty(summary),
                nullToEmpty(parentFeedback), nullToEmpty(followUp), context.batchId);
        context.communications++;
    }

    private void upsertLessonPlan(ImportContext context, String legacy, UUID studentId, String date,
                                  String chapter, String knowledgePoints, String focus, String difficulty,
                                  String review, String newContent, String homework, String notes) {
        if (legacy == null || studentId == null) {
            return;
        }
        jdbc.update("""
                insert into lesson_plans (id, legacy_id, student_id, planned_date, chapter, knowledge_points,
                    focus, difficulty, review, new_content, homework, notes, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id,
                    planned_date = excluded.planned_date, chapter = excluded.chapter,
                    knowledge_points = excluded.knowledge_points, focus = excluded.focus,
                    difficulty = excluded.difficulty, review = excluded.review,
                    new_content = excluded.new_content, homework = excluded.homework,
                    notes = excluded.notes, import_batch_id = excluded.import_batch_id, updated_at = now()
                """, Ids.fromLegacy("lesson-plan", legacy), legacy, studentId, parseDate(date),
                nullToEmpty(chapter), nullToEmpty(knowledgePoints), nullToEmpty(focus),
                nullToEmpty(difficulty), nullToEmpty(review), nullToEmpty(newContent),
                nullToEmpty(homework), nullToEmpty(notes), context.batchId);
    }

    private void upsertTask(ImportContext context, String legacy, String title, String studentLegacy,
                            String type, String dueDate, String priority, String status, String notes) {
        if (legacy == null) {
            return;
        }
        jdbc.update("""
                insert into tasks (id, legacy_id, title, student_id, type, due_date, priority, status, notes, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set title = excluded.title, student_id = excluded.student_id,
                    type = excluded.type, due_date = excluded.due_date, priority = excluded.priority,
                    status = excluded.status, notes = excluded.notes, import_batch_id = excluded.import_batch_id,
                    updated_at = now()
                """, Ids.fromLegacy("task", legacy), legacy, title,
                studentLegacy == null ? null : studentIdOf(context, studentLegacy), nullToEmpty(type),
                parseDate(dueDate), nullToEmpty(priority), nullToEmpty(status), nullToEmpty(notes), context.batchId);
    }

    private void upsertParentContact(ImportContext context, String legacy, String studentLegacy, String name,
                                     String phone, String relation, boolean primary, String notes) {
        String effectiveLegacy = legacy == null
                ? "contact-" + Ids.sha256Hex(studentLegacy + ":" + name + ":" + relation)
                : legacy;
        jdbc.update("""
                insert into parent_contacts (id, legacy_id, student_id, name, phone, relation, is_primary, notes,
                    import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id, name = excluded.name,
                    phone = excluded.phone, relation = excluded.relation, is_primary = excluded.is_primary,
                    notes = excluded.notes, import_batch_id = excluded.import_batch_id, updated_at = now()
                """, Ids.fromLegacy("parent-contact", effectiveLegacy), effectiveLegacy,
                studentIdOf(context, studentLegacy), nullToEmpty(name), nullToEmpty(phone),
                nullToEmpty(relation), primary, nullToEmpty(notes), context.batchId);
    }

    private void upsertProblem(ImportContext context, String legacy, UUID studentId, String title,
                               String description, String date, String status) {
        if (legacy == null || studentId == null) {
            return;
        }
        jdbc.update("""
                insert into mistake_records (id, legacy_id, student_id, mistake_type, mistake_reason,
                    first_seen_at, last_seen_at, repeat_count, status, import_batch_id)
                values (?, ?, ?, 'UNKNOWN', ?, ?, ?, 1, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id,
                    mistake_reason = excluded.mistake_reason, last_seen_at = excluded.last_seen_at,
                    status = excluded.status, import_batch_id = excluded.import_batch_id,
                    version = mistake_records.version + 1, updated_at = now()
                """, Ids.fromLegacy("mistake", legacy), legacy, studentId,
                (nullToEmpty(title) + "\n" + nullToEmpty(description)).trim(),
                timestampOrNow(date), timestampOrNow(date), status, context.batchId);
        context.problems++;
    }

    private void upsertProblem(ImportContext context, String legacy, String studentLegacy, String title,
                               String description, String date, String status) {
        upsertProblem(context, legacy, studentIdOf(context, studentLegacy), title, description, date, status);
    }

    private void upsertHistorical(ImportContext context, String legacy, String studentLegacy, String date,
                                  String accuracy, String subject, String title, String content, String source) {
        if (legacy == null || studentLegacy == null) {
            return;
        }
        jdbc.update("""
                insert into historical_learning_records (id, legacy_id, student_id, record_date, date_accuracy,
                    subject_id, title, content, source, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id,
                    record_date = excluded.record_date, date_accuracy = excluded.date_accuracy,
                    subject_id = excluded.subject_id, title = excluded.title, content = excluded.content,
                    source = excluded.source, import_batch_id = excluded.import_batch_id
                """, Ids.fromLegacy("historical", legacy), legacy, studentIdOf(context, studentLegacy),
                parseDate(date), nullToEmpty(accuracy), subjectId(context, subject), title,
                nullToEmpty(content), nullToEmpty(source), context.batchId);
    }

    private void upsertStudentProgress(ImportContext context, String legacy, UUID studentId, String date,
                                       String accuracy, String category, String title, String detail, String source) {
        if (legacy == null || studentId == null) {
            return;
        }
        jdbc.update("""
                insert into student_progress (id, legacy_id, student_id, date, date_accuracy, category, title,
                    detail, source, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id, date = excluded.date,
                    date_accuracy = excluded.date_accuracy, category = excluded.category, title = excluded.title,
                    detail = excluded.detail, source = excluded.source, import_batch_id = excluded.import_batch_id
                """, Ids.fromLegacy("progress", legacy), legacy, studentId, parseDate(date),
                nullToEmpty(accuracy), nullToEmpty(category), nullToEmpty(title), nullToEmpty(detail),
                nullToEmpty(source), context.batchId);
    }

    private void upsertUnresolved(ImportContext context, String legacy, String date, String accuracy,
                                  String title, String content, String source) {
        String effectiveLegacy = legacy == null
                ? "unresolved-" + Ids.sha256Hex(title + ":" + content).substring(0, 16)
                : legacy;
        jdbc.update("""
                insert into unresolved_historical_records (id, legacy_id, record_date, date_accuracy, title,
                    content, source, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set record_date = excluded.record_date,
                    date_accuracy = excluded.date_accuracy, title = excluded.title, content = excluded.content,
                    source = excluded.source, import_batch_id = excluded.import_batch_id
                """, Ids.fromLegacy("unresolved", effectiveLegacy), effectiveLegacy, parseDate(date),
                nullToEmpty(accuracy), nullToEmpty(title), nullToEmpty(content), nullToEmpty(source),
                context.batchId);
    }

    private void upsertHomework(ImportContext context, String legacy, String studentLegacy, String courseLegacy,
                                String name, String assignedDate, String dueDate, String content,
                                String status, String teacherComment) {
        if (legacy == null || studentLegacy == null) {
            return;
        }
        jdbc.update("""
                insert into homework (id, legacy_id, student_id, course_id, name, assigned_date, due_date,
                    content, status, teacher_comment, import_batch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (legacy_id) do update set student_id = excluded.student_id,
                    course_id = excluded.course_id, name = excluded.name,
                    assigned_date = excluded.assigned_date, due_date = excluded.due_date,
                    content = excluded.content, status = excluded.status,
                    teacher_comment = excluded.teacher_comment, import_batch_id = excluded.import_batch_id,
                    updated_at = now()
                """, Ids.fromLegacy("homework", legacy), legacy, studentIdOf(context, studentLegacy),
                courseLegacy == null ? null : Ids.fromLegacy("course", courseLegacy), nullToEmpty(name),
                parseDate(assignedDate), parseDate(dueDate), nullToEmpty(content),
                nullToEmpty(status), nullToEmpty(teacherComment), context.batchId);
    }

    // ---------------------------------------------------------------- helpers

    private UUID subjectId(ImportContext context, String nameOrCode) {
        if (nameOrCode == null || nameOrCode.isBlank()) {
            return otherSubjectId(context);
        }
        String key = nameOrCode.trim().toLowerCase(Locale.ROOT);
        UUID cached = context.subjectIds.get(key);
        if (cached != null) {
            return cached;
        }
        List<UUID> found = jdbc.query("""
                select id from subjects where lower(name) = lower(?) or lower(code) = lower(?) limit 1
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), nameOrCode, nameOrCode);
        UUID id = found.isEmpty() ? otherSubjectId(context) : found.get(0);
        if (found.isEmpty()) {
            issue(context, "WARN", "subject", nameOrCode, "未知学科，归入 OTHER");
        }
        context.subjectIds.put(key, id);
        return id;
    }

    private UUID otherSubjectId(ImportContext context) {
        UUID cached = context.subjectIds.get("other");
        if (cached != null) {
            return cached;
        }
        UUID id = jdbc.queryForObject("select id from subjects where code = 'OTHER'", UUID.class);
        context.subjectIds.put("other", id);
        return id;
    }

    private UUID studentIdOf(ImportContext context, String legacy) {
        if (legacy == null) {
            return null;
        }
        UUID cached = context.studentIds.get(legacy);
        if (cached != null) {
            return cached;
        }
        List<UUID> found = jdbc.query("select id from students where legacy_id = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class), legacy);
        UUID id = found.isEmpty() ? Ids.fromLegacy("student", legacy) : found.get(0);
        if (found.isEmpty()) {
            issue(context, "WARN", "student", legacy, "引用了不存在的学生，按 legacy id 生成 UUID");
        }
        context.studentIds.put(legacy, id);
        return id;
    }

    private UUID idOf(ImportContext context, JsonNode node, String field) {
        String legacy = text(node, field, null);
        return legacy == null ? null : studentIdOf(context, legacy);
    }

    private void insertJob(ImportContext context) {
        jdbc.update("""
                insert into import_jobs (id, source_name, source_sha256, source_format, status, report)
                values (?, ?, ?, ?, 'RUNNING', '{}'::jsonb)
                """, context.batchId, context.sourceName, context.sha256, context.format);
    }

    private void finishJob(ImportContext context, Result result) {
        jdbc.update("""
                update import_jobs set status = 'COMPLETED', students_imported = ?, sessions_imported = ?,
                    questions_imported = ?, issues_count = ?, report = ?::jsonb, finished_at = now()
                where id = ?
                """, result.students(), result.courses(), result.questions(), result.issues(),
                reportJson(result), context.batchId);
        for (Issue issue : context.issues.stream().limit(500).toList()) {
            jdbc.update("""
                    insert into import_issues (id, import_job_id, severity, entity_type, source_ref, message)
                    values (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), context.batchId, issue.severity(), issue.entityType(),
                    issue.sourceRef(), issue.message());
        }
    }

    private void recordFailedJob(ImportContext context, RuntimeException exception) {
        try {
            jdbc.update("""
                    insert into import_jobs (id, source_name, source_sha256, source_format, status, issues_count,
                        report, finished_at)
                    values (?, ?, ?, ?, 'FAILED', 1, ?::jsonb, now())
                    on conflict (id) do update set status = 'FAILED', issues_count = 1,
                        report = excluded.report, finished_at = now()
                    """, context.batchId, context.sourceName, context.sha256, context.format,
                    "{\"error\":\"" + exception.getMessage().replace("\"", "'") + "\"}");
        } catch (RuntimeException logFailure) {
            log.error("Unable to record failed migration job {}", context.batchId, logFailure);
        }
    }

    private Result result(ImportContext context, String actor) {
        return new Result(context.batchId, context.format, context.sourceName, context.sha256,
                context.dryRun, context.students, context.courses, context.scores, context.knowledgeIds.size(),
                context.communications, context.problems, context.questions, context.issues.size());
    }

    private String reportJson(Result result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private void issue(ImportContext context, String severity, String entityType, String ref, String message) {
        if (context.issues.size() < 500) {
            context.issues.add(new Issue(severity, entityType, ref, message));
        }
    }

    private static Iterable<JsonNode> array(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        node.forEach(items::add);
        return items;
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode array = node.path(field);
        if (!array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.isObject()) {
                String value = item.path("value").asText("");
                if (!value.isBlank()) {
                    values.add(value);
                }
            } else if (!item.isNull()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static int intValue(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private static double doubleValue(JsonNode node, String field, double fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : fallback;
    }

    private static long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asLong() : fallback;
    }

    private static boolean boolValue(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }

    private static void copyIfPresent(JsonNode source, com.fasterxml.jackson.databind.node.ObjectNode target, String field) {
        if (source.has(field)) {
            target.set(field, source.get(field));
        }
    }

    private static UUID nullableUuid(String value, String kind) {
        return value == null || value.isBlank() ? null : Ids.fromLegacy(kind, value);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(trimmed, SLASH_DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static java.sql.Timestamp timestampOrNow(String value) {
        if (value == null || value.isBlank()) {
            return new java.sql.Timestamp(System.currentTimeMillis());
        }
        try {
            return java.sql.Timestamp.from(Instant.parse(value));
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            long epoch = Long.parseLong(value.trim());
            return new java.sql.Timestamp(epoch);
        } catch (RuntimeException ignored) {
            // fall through
        }
        LocalDate date = parseDate(value);
        return date == null
                ? new java.sql.Timestamp(System.currentTimeMillis())
                : java.sql.Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static java.sql.Timestamp timestamp(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber()) {
            return new java.sql.Timestamp(value.asLong());
        }
        if (value.isTextual()) {
            try {
                return java.sql.Timestamp.from(Instant.parse(value.asText()));
            } catch (RuntimeException ignored) {
                return new java.sql.Timestamp(System.currentTimeMillis());
            }
        }
        return new java.sql.Timestamp(System.currentTimeMillis());
    }

    private static String safeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "PAUSED", "ENDED", "ARCHIVED", "UNKNOWN" -> status.toUpperCase(Locale.ROOT);
            default -> "UNKNOWN";
        };
    }

    private static String mapProblemStatus(String status) {
        if (status == null) {
            return "OPEN";
        }
        return switch (status) {
            case "已解决", "完成", "RESOLVED" -> "RESOLVED";
            case "已忽略", "IGNORED" -> "IGNORED";
            case "复习中", "REVIEWING" -> "REVIEWING";
            default -> "OPEN";
        };
    }

    private static String textArray(String jsonArray) {
        if (jsonArray == null || jsonArray.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonArray);
            if (!node.isArray()) {
                return "{}";
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> values.add(item.asText()));
            return "{" + String.join(",", values.stream().map(value ->
                    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").toList()) + "}";
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class ImportContext {
        private final UUID batchId;
        private final String sourceName;
        private final String format;
        private final String sha256;
        private final boolean dryRun;
        private final Map<String, UUID> studentIds = new LinkedHashMap<>();
        private final Map<String, UUID> knowledgeIds = new LinkedHashMap<>();
        private final Map<String, UUID> subjectIds = new HashMap<>();
        private final List<Issue> issues = new ArrayList<>();
        private int students;
        private int courses;
        private int scores;
        private int communications;
        private int problems;
        private int questions;

        private ImportContext(UUID batchId, String sourceName, String format, String sha256, boolean dryRun) {
            this.batchId = batchId;
            this.sourceName = sourceName;
            this.format = format;
            this.sha256 = sha256;
            this.dryRun = dryRun;
        }
    }
}
