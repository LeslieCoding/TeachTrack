package com.leslie.teachtrack.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.common.Ids;
import com.leslie.teachtrack.question.QuestionAnswer;
import com.leslie.teachtrack.question.QuestionDetail;
import com.leslie.teachtrack.question.QuestionService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {
    public record Operation(
            String operationId,
            String entityType,
            String entityId,
            String operation,
            Integer baseVersion,
            Boolean force,
            JsonNode payload
    ) {
    }

    public record Conflict(
            String operationId,
            String entityType,
            String entityId,
            int baseVersion,
            int currentVersion,
            JsonNode remote,
            List<String> choices
    ) {
    }

    public record ApplyResult(String operationId, String status, String entityType, String entityId, Integer version) {
    }

    public record Result(List<ApplyResult> applied, List<Conflict> conflicts) {
    }

    private static final List<String> CHOICES = List.of("KEEP_LOCAL", "KEEP_REMOTE", "MANUAL_MERGE");
    private static final List<String> STUDENT_FIELDS = List.of(
            "fullName", "displayName", "status", "gender", "currentGrade", "gradeAtRecord",
            "nameCompleteness", "school", "className", "subjectId", "notes");
    private static final List<String> COURSE_FIELDS = List.of(
            "date", "startTime", "endTime", "subjectId", "lessonTitle", "lessonContent",
            "performance", "problems", "homeworkSummary", "nextLessonPlan", "teacherNotes", "learningStatus");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SyncChangeRepository changes;
    private final AuditService audit;
    private final QuestionService questions;

    public SyncService(JdbcTemplate jdbc, ObjectMapper mapper, SyncChangeRepository changes,
                       AuditService audit, QuestionService questions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.changes = changes;
        this.audit = audit;
        this.questions = questions;
    }

    @Transactional
    public Result push(AuthPrincipal principal, String deviceId, List<Operation> operations) {
        List<ApplyResult> applied = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        for (Operation operation : operations) {
            String operationId = operation.operationId() == null || operation.operationId().isBlank()
                    ? UUID.randomUUID().toString() : operation.operationId();
            var previous = jdbc.query("""
                            select status, result_version from sync_operations where operation_id = ?
                            """, (rs, rowNum) -> new Object() {
                        String status = rs.getString("status");
                        Integer version = rs.getObject("result_version", Integer.class);
                    }, operationId).stream().findFirst();
            if (previous.isPresent()) {
                applied.add(new ApplyResult(operationId, previous.get().status,
                        operation.entityType(), operation.entityId(), previous.get().version));
                continue;
            }
            switch (operation.entityType() == null ? "" : operation.entityType()) {
                case "student" -> applyStudent(principal, deviceId, operationId, operation, applied, conflicts);
                case "courseSession", "course_session" ->
                        applyCourse(principal, deviceId, operationId, operation, applied, conflicts);
                case "question" -> applyQuestion(principal, deviceId, operationId, operation, applied, conflicts);
                default -> throw ApiException.badRequest("UNSUPPORTED_ENTITY",
                        "同步暂不支持实体: " + operation.entityType());
            }
        }
        return new Result(applied, conflicts);
    }

    private void applyStudent(AuthPrincipal principal, String deviceId, String operationId,
                              Operation operation, List<ApplyResult> applied, List<Conflict> conflicts) {
        JsonNode payload = payload(operation);
        String legacy = text(payload, "legacyId", operation.entityId());
        UUID id = Ids.fromLegacy("student", legacy);
        Integer currentVersion = currentVersion("students", id);
        int base = operation.baseVersion() == null ? 0 : operation.baseVersion();
        if (currentVersion != null && currentVersion != base && !Boolean.TRUE.equals(operation.force())) {
            conflicts.add(conflict(operationId, "student", id.toString(), base, currentVersion, studentRemote(id)));
            recordOperation(operationId, deviceId, "student", id, base, currentVersion, "CONFLICT");
            return;
        }
        String displayName = text(payload, "displayName", text(payload, "fullName", "未命名"));
        UUID subjectId = uuid(payload, "subjectId");
        int version;
        if (currentVersion == null) {
            jdbc.update("""
                    insert into students (id, legacy_id, full_name, display_name, status, gender, current_grade,
                        grade_at_record, name_completeness, school, class_name, subject_id, notes, version)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, id, legacy, nullableText(payload, "fullName"), displayName,
                    safeStatus(text(payload, "status", "ACTIVE")), nullableText(payload, "gender"),
                    nullableText(payload, "currentGrade"), nullableText(payload, "gradeAtRecord"),
                    text(payload, "nameCompleteness", "CONFIRMED"), text(payload, "school", ""),
                    text(payload, "className", ""), subjectId, text(payload, "notes", ""));
            version = 1;
        } else {
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            for (String field : STUDENT_FIELDS) {
                if (!payload.has(field)) {
                    continue;
                }
                sets.add(snake(field) + " = ?");
                args.add(toColumnValue(field, payload.get(field)));
            }
            if (sets.isEmpty()) {
                version = currentVersion;
            } else {
                sets.add("version = version + 1");
                sets.add("updated_at = now()");
                args.add(id);
                args.add(currentVersion);
                jdbc.update("update students set " + String.join(", ", sets)
                        + " where id = ? and version = ?", args.toArray());
                version = currentVersion + 1;
            }
        }
        recordOperation(operationId, deviceId, "student", id, base, version, "APPLIED");
        changes.append("student", id, "UPSERT", version,
                deviceId == null ? null : parseUuid(deviceId), "{\"id\":\"" + id + "\"}");
        audit.record(principal, "sync.student.upsert", "Student", id, "SUCCESS", null);
        applied.add(new ApplyResult(operationId, "APPLIED", "student", id.toString(), version));
    }

    private void applyCourse(AuthPrincipal principal, String deviceId, String operationId,
                             Operation operation, List<ApplyResult> applied, List<Conflict> conflicts) {
        JsonNode payload = payload(operation);
        String legacy = text(payload, "legacyId", operation.entityId());
        UUID id = Ids.fromLegacy("course", legacy);
        Integer currentVersion = currentVersion("course_sessions", id);
        int base = operation.baseVersion() == null ? 0 : operation.baseVersion();
        if (currentVersion != null && currentVersion != base && !Boolean.TRUE.equals(operation.force())) {
            conflicts.add(conflict(operationId, "courseSession", id.toString(), base, currentVersion, courseRemote(id)));
            recordOperation(operationId, deviceId, "courseSession", id, base, currentVersion, "CONFLICT");
            return;
        }
        UUID studentId = Ids.fromLegacy("student", text(payload, "studentId", ""));
        if (studentId == null || jdbc.queryForObject("select count(*) from students where id = ?", Integer.class, studentId) == 0) {
            throw ApiException.badRequest("STUDENT_NOT_FOUND", "课程记录引用的学生不存在");
        }
        LocalDate date = parseDate(nullableText(payload, "date"));
        int version;
        if (currentVersion == null) {
            jdbc.update("""
                    insert into course_sessions (id, legacy_id, student_id, date, start_time, end_time, subject_id,
                        lesson_title, lesson_content, performance, problems, homework_summary, next_lesson_plan,
                        teacher_notes, learning_status, version)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, id, legacy, studentId, date, text(payload, "startTime", ""),
                    text(payload, "endTime", ""), uuid(payload, "subjectId"),
                    text(payload, "lessonTitle", text(payload, "title", "")),
                    text(payload, "lessonContent", text(payload, "content", "")),
                    text(payload, "performance", ""), text(payload, "problems", ""),
                    text(payload, "homeworkSummary", ""), text(payload, "nextLessonPlan", ""),
                    text(payload, "teacherNotes", ""), text(payload, "learningStatus", ""));
            version = 1;
        } else {
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            for (String field : COURSE_FIELDS) {
                if (!payload.has(field)) {
                    continue;
                }
                sets.add(snake(field) + " = ?");
                args.add(toColumnValue(field, payload.get(field)));
            }
            if (sets.isEmpty()) {
                version = currentVersion;
            } else {
                sets.add("version = version + 1");
                sets.add("updated_at = now()");
                args.add(id);
                args.add(currentVersion);
                jdbc.update("update course_sessions set " + String.join(", ", sets)
                        + " where id = ? and version = ?", args.toArray());
                version = currentVersion + 1;
            }
        }
        recordOperation(operationId, deviceId, "courseSession", id, base, version, "APPLIED");
        changes.append("courseSession", id, "UPSERT", version,
                deviceId == null ? null : parseUuid(deviceId), "{\"id\":\"" + id + "\"}");
        audit.record(principal, "sync.course.upsert", "CourseSession", id, "SUCCESS", null);
        applied.add(new ApplyResult(operationId, "APPLIED", "courseSession", id.toString(), version));
    }

    private void applyQuestion(AuthPrincipal principal, String deviceId, String operationId,
                               Operation operation, List<ApplyResult> applied, List<Conflict> conflicts) {
        JsonNode payload = payload(operation);
        String rawId = text(payload, "id", operation.entityId());
        UUID id = Ids.fromLegacy("question", rawId);
        QuestionDetail current;
        try {
            current = questions.get(id);
        } catch (ApiException notFound) {
            current = null;
        }
        int base = operation.baseVersion() == null ? 0 : operation.baseVersion();
        if (current != null && current.question().version() != base && !Boolean.TRUE.equals(operation.force())) {
            conflicts.add(conflict(operationId, "question", id.toString(), base, current.question().version(),
                    mapper.valueToTree(current)));
            recordOperation(operationId, deviceId, "question", id, base, current.question().version(), "CONFLICT");
            return;
        }
        if (current == null) {
            QuestionAnswer.CreateRequest request = new QuestionAnswer.CreateRequest(
                    uuid(payload, "subjectId"),
                    text(payload, "grade", ""),
                    text(payload, "textbookVersion", ""),
                    text(payload, "questionType", "OTHER"),
                    payload.path("difficulty").asInt(3),
                    text(payload, "stem", ""),
                    text(payload, "sourceType", "TEACHER_IMPORTED"),
                    text(payload, "sourceName", ""),
                    text(payload, "copyrightStatus", "PERSONAL_USE"),
                    text(payload, "usageScope", "PRIVATE_TEACHING"),
                    text(payload, "reviewStatus", "DRAFT"),
                    null, null, null, null);
            QuestionDetail created = questions.create(request, principal);
            recordOperation(operationId, deviceId, "question", created.question().id(), base,
                    created.question().version(), "APPLIED");
            applied.add(new ApplyResult(operationId, "APPLIED", "question",
                    created.question().id().toString(), created.question().version()));
            return;
        }
        QuestionAnswer.UpdateRequest request = new QuestionAnswer.UpdateRequest(
                current.question().version(),
                uuid(payload, "subjectId") == null ? current.question().subjectId() : uuid(payload, "subjectId"),
                text(payload, "grade", current.question().grade()),
                text(payload, "textbookVersion", current.question().textbookVersion()),
                text(payload, "questionType", current.question().questionType()),
                payload.has("difficulty") ? payload.get("difficulty").asInt() : current.question().difficulty(),
                text(payload, "stem", current.question().stem()),
                text(payload, "sourceType", current.question().sourceType()),
                text(payload, "sourceName", current.question().sourceName()),
                text(payload, "copyrightStatus", current.question().copyrightStatus()),
                text(payload, "usageScope", current.question().usageScope()),
                text(payload, "reviewStatus", current.question().reviewStatus()),
                null, null, null, null);
        QuestionDetail updated = questions.update(id, request, principal);
        recordOperation(operationId, deviceId, "question", id, base, updated.question().version(), "APPLIED");
        applied.add(new ApplyResult(operationId, "APPLIED", "question", id.toString(), updated.question().version()));
    }

    private Conflict conflict(String operationId, String type, String id, int base, int current, JsonNode remote) {
        return new Conflict(operationId, type, id, base, current, remote, CHOICES);
    }

    private JsonNode studentRemote(UUID id) {
        ObjectNode node = mapper.createObjectNode();
        jdbc.query("select * from students where id = ?", rs -> {
            node.put("id", id.toString());
            node.put("displayName", rs.getString("display_name"));
            node.put("fullName", rs.getString("full_name"));
            node.put("status", rs.getString("status"));
            node.put("version", rs.getInt("version"));
        }, id);
        return node;
    }

    private JsonNode courseRemote(UUID id) {
        ObjectNode node = mapper.createObjectNode();
        jdbc.query("select * from course_sessions where id = ?", rs -> {
            node.put("id", id.toString());
            node.put("lessonTitle", rs.getString("lesson_title"));
            node.put("date", rs.getString("date"));
            node.put("version", rs.getInt("version"));
        }, id);
        return node;
    }

    private Integer currentVersion(String table, UUID id) {
        return jdbc.query("select version from " + table + " where id = ? and deleted_at is null",
                (rs, rowNum) -> rs.getInt("version"), id).stream().findFirst().orElse(null);
    }

    private void recordOperation(String operationId, String deviceId, String entityType, UUID entityId,
                                 int base, int resultVersion, String status) {
        jdbc.update("""
                insert into sync_operations (id, operation_id, device_id, entity_type, entity_id,
                    base_version, result_version, status)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), operationId,
                deviceId == null ? null : parseUuid(deviceId), entityType, entityId,
                base, resultVersion, status);
    }

    private static JsonNode payload(Operation operation) {
        return operation.payload() == null ? new ObjectMapper().createObjectNode() : operation.payload();
    }

    private static String snake(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static Object toColumnValue(String field, JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (field.endsWith("Id")) {
            return parseUuid(value.asText());
        }
        if ("date".equals(field)) {
            return parseDate(value.asText());
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return value.asText();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return Ids.v5("unknown", value);
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        String value = nullableText(node, field);
        return value == null ? null : parseUuid(value);
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = nullableText(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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

    private static String safeStatus(String status) {
        String normalized = status == null ? "ACTIVE" : status.toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "UNKNOWN", "ACTIVE", "PAUSED", "ENDED", "ARCHIVED" -> normalized;
            default -> "ACTIVE";
        };
    }
}
