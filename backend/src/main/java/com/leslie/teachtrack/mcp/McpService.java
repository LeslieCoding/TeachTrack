package com.leslie.teachtrack.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leslie.teachtrack.audit.AuditService;
import com.leslie.teachtrack.auth.AuthPrincipal;
import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.exam.ExamRequests;
import com.leslie.teachtrack.exam.ExamService;
import com.leslie.teachtrack.practice.MasteryModels;
import com.leslie.teachtrack.practice.PracticeModels;
import com.leslie.teachtrack.practice.PracticeRepository;
import com.leslie.teachtrack.practice.PracticeService;
import com.leslie.teachtrack.question.QuestionService;
import com.leslie.teachtrack.student.StudentRepository;
import com.leslie.teachtrack.student.StudentRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Minimal MCP JSON-RPC server. Only business tools are registered; delete/bulk/reset
 * operations do not exist on this surface. Every call is audited as actor_type=AI.
 */
@Service
public class McpService {
    public record Tool(String name, String description, boolean write, Map<String, Object> inputSchema) {
    }

    private final ObjectMapper mapper;
    private final StudentRepository students;
    private final com.leslie.teachtrack.course.CourseSessionRepository courses;
    private final QuestionService questions;
    private final ExamService exams;
    private final PracticeService practices;
    private final PracticeRepository practiceRepository;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public McpService(ObjectMapper mapper, StudentRepository students,
                      com.leslie.teachtrack.course.CourseSessionRepository courses,
                      QuestionService questions, ExamService exams, PracticeService practices,
                      PracticeRepository practiceRepository, JdbcTemplate jdbc, AuditService audit) {
        this.mapper = mapper;
        this.students = students;
        this.courses = courses;
        this.questions = questions;
        this.exams = exams;
        this.practices = practices;
        this.practiceRepository = practiceRepository;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public ObjectNode handle(JsonNode request, AuthPrincipal principal) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        JsonNode id = request.get("id");
        if (id != null) {
            response.set("id", id);
        }
        String method = request.path("method").asText("");
        try {
            switch (method) {
                case "initialize" -> response.set("result", initialize());
                case "notifications/initialized" -> {
                    return null;
                }
                case "tools/list" -> response.set("result", toolsList());
                case "tools/call" -> response.set("result", callTool(request.path("params"), principal,
                        id == null ? "" : id.asText()));
                default -> error(response, -32601, "Method not found: " + method);
            }
        } catch (ApiException exception) {
            error(response, -32000, exception.getMessage());
        } catch (Exception exception) {
            error(response, -32603, exception.getMessage() == null ? "Internal error" : exception.getMessage());
        }
        return response;
    }

    private ObjectNode initialize() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "teachtrack");
        serverInfo.put("version", "4.0.0-alpha.1");
        return result;
    }

    private ObjectNode toolsList() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        for (Tool tool : TOOLS) {
            ObjectNode node = tools.addObject();
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.set("inputSchema", mapper.valueToTree(tool.inputSchema()));
        }
        return result;
    }

    private ObjectNode callTool(JsonNode params, AuthPrincipal principal, String requestId) {
        String name = params.path("name").asText("");
        JsonNode args = params.path("arguments");
        Tool tool = TOOLS.stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_TOOL", "Unknown tool: " + name));
        if (!principal.hasScope("ai.read") && !principal.hasScope("owner")) {
            throw ApiException.forbidden("Token lacks ai.read");
        }
        if (tool.write() && !principal.hasScope("ai.write.draft") && !principal.hasScope("owner")) {
            throw ApiException.forbidden("Token lacks ai.write.draft");
        }
        try {
            Object data = execute(name, args, principal);
            audit.recordAi(principal, "mcp", "client", requestId, "mcp." + name,
                    entityType(name), null, "SUCCESS", null);
            return content(data);
        } catch (RuntimeException exception) {
            audit.recordAi(principal, "mcp", "client", requestId, "mcp." + name,
                    entityType(name), null, "FAILURE", "TOOL_ERROR");
            throw exception;
        }
    }

    private Object execute(String name, JsonNode args, AuthPrincipal principal) {
        return switch (name) {
            case "search_students" -> students.list(null, text(args, "query", ""),
                    intArg(args, "limit", 20), 0);
            case "get_student_profile" -> profile(requireStudent(args));
            case "get_student_sessions" -> courses.findByStudent(requireStudent(args).id(),
                    intArg(args, "limit", 50));
            case "get_student_mastery" -> practiceRepository.mastery(requireStudent(args).id());
            case "get_student_mistakes" -> mistakes(requireStudent(args).id(), intArg(args, "limit", 50));
            case "search_questions" -> questions.search(
                    uuidOrNull(args, "subjectId"), text(args, "grade", null),
                    text(args, "questionType", null), intOrNull(args, "minDifficulty"),
                    intOrNull(args, "maxDifficulty"), uuidOrNull(args, "knowledgePointId"),
                    text(args, "sourceType", null), text(args, "reviewStatus", null),
                    text(args, "query", ""), intArg(args, "limit", 20), intArg(args, "offset", 0));
            case "get_question" -> questions.get(UUID.fromString(text(args, "id", "")));
            case "analyze_exam" -> analyze(args, principal);
            case "generate_practice", "save_practice_draft" -> generate(args, principal);
            case "get_practice" -> practices.detail(UUID.fromString(text(args, "id", "")));
            default -> throw ApiException.badRequest("UNKNOWN_TOOL", "Unknown tool: " + name);
        };
    }

    private Map<String, Object> profile(StudentRow student) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", student.id());
        result.put("legacyId", student.legacyId());
        result.put("displayName", student.displayName());
        result.put("fullName", student.fullName());
        result.put("status", student.status());
        result.put("currentGrade", student.currentGrade());
        result.put("gradeAtRecord", student.gradeAtRecord());
        result.put("school", student.school());
        result.put("notes", student.notes());
        result.put("sessions", courses.findByStudent(student.id(), 200).size());
        result.put("mastery", practiceRepository.mastery(student.id()));
        // Parent contact / phone are deliberately not exposed to external AI.
        return result;
    }

    private List<Map<String, Object>> mistakes(UUID studentId, int limit) {
        return jdbc.query("""
                select id, question_id, knowledge_point_id, mistake_type, mistake_reason, repeat_count,
                       status, last_seen_at
                from mistake_records where student_id = ? order by last_seen_at desc limit ?
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getObject("id", UUID.class));
            row.put("questionId", rs.getObject("question_id", UUID.class));
            row.put("knowledgePointId", rs.getObject("knowledge_point_id", UUID.class));
            row.put("mistakeType", rs.getString("mistake_type"));
            row.put("mistakeReason", rs.getString("mistake_reason"));
            row.put("repeatCount", rs.getInt("repeat_count"));
            row.put("status", rs.getString("status"));
            row.put("lastSeenAt", rs.getTimestamp("last_seen_at"));
            return row;
        }, studentId, Math.max(1, Math.min(limit, 200)));
    }

    private Object analyze(JsonNode args, AuthPrincipal principal) {
        UUID paperId = UUID.fromString(text(args, "examId", text(args, "paperId", "")));
        List<ExamRequests.ParsedQuestionInput> parsed = new ArrayList<>();
        JsonNode questionsNode = args.path("questions");
        if (questionsNode.isArray()) {
            for (JsonNode node : questionsNode) {
                parsed.add(new ExamRequests.ParsedQuestionInput(
                        text(node, "number", ""), text(node, "type", "OTHER"),
                        doubleOrNull(node, "score"), doubleOrNull(node, "fullScore"),
                        text(node, "studentAnswer", ""), text(node, "standardAnswer", ""),
                        text(node, "stem", "")));
            }
        }
        return exams.analyze(paperId, new ExamRequests.AnalyzeRequest(text(args, "text", ""), parsed), principal);
    }

    private Object generate(JsonNode args, AuthPrincipal principal) {
        List<String> knowledgeIds = new ArrayList<>();
        JsonNode ids = args.path("knowledgePointIds");
        if (ids.isArray()) {
            ids.forEach(node -> knowledgeIds.add(node.asText()));
        }
        return practices.generate(new PracticeModels.GenerateRequest(
                text(args, "studentId", ""), text(args, "subjectId", ""), knowledgeIds,
                intArg(args, "questionCount", 10), intOrNull(args, "minDifficulty"),
                intOrNull(args, "maxDifficulty"), doubleOrNull(args, "totalScore"),
                args.path("includeMistakes").asBoolean(false), args.path("allowAi").asBoolean(true),
                text(args, "mode", "BASIC"), text(args, "title", null)
        ), principal);
    }

    private StudentRow requireStudent(JsonNode args) {
        String id = text(args, "studentId", text(args, "id", ""));
        return students.find(id).orElseThrow(() -> ApiException.notFound("学生不存在: " + id));
    }

    private static String entityType(String tool) {
        return switch (tool) {
            case "search_students", "get_student_profile", "get_student_sessions",
                 "get_student_mastery", "get_student_mistakes" -> "Student";
            case "search_questions", "get_question" -> "Question";
            case "analyze_exam" -> "ExamPaper";
            case "generate_practice", "save_practice_draft", "get_practice" -> "PracticeSet";
            default -> "Tool";
        };
    }

    private ObjectNode content(Object data) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        try {
            text.put("text", mapper.writeValueAsString(data));
        } catch (Exception exception) {
            text.put("text", String.valueOf(data));
        }
        result.put("isError", false);
        return result;
    }

    private static void error(ObjectNode response, int code, String message) {
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private static int intArg(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : fallback;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        String value = text(node, field, null);
        return value == null ? null : UUID.fromString(value);
    }

    private static Map<String, Object> schema(String type, Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("properties", properties);
        if (required != null) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static final List<Tool> TOOLS = List.of(
            new Tool("search_students", "Search students by display name. No parent contact data.",
                    false, schema("object", Map.of("query", prop("string", "name fragment"),
                    "limit", prop("integer", "max results")), List.of("query"))),
            new Tool("get_student_profile", "Get a de-identified student profile with mastery summary.",
                    false, schema("object", Map.of("studentId", prop("string", "UUID or legacy id")),
                    List.of("studentId"))),
            new Tool("get_student_sessions", "List course sessions for a student.",
                    false, schema("object", Map.of("studentId", prop("string", "UUID or legacy id"),
                    "limit", prop("integer", "max results")), List.of("studentId"))),
            new Tool("get_student_mastery", "Get mastery scores and confidence per knowledge point.",
                    false, schema("object", Map.of("studentId", prop("string", "UUID or legacy id")),
                    List.of("studentId"))),
            new Tool("get_student_mistakes", "List mistake records for a student.",
                    false, schema("object", Map.of("studentId", prop("string", "UUID or legacy id"),
                    "limit", prop("integer", "max results")), List.of("studentId"))),
            new Tool("search_questions", "Search the approved/teacher-owned question bank.",
                    false, schema("object", Map.of("query", prop("string", "stem fragment"),
                    "subjectId", prop("string", "subject UUID"),
                    "grade", prop("string", "grade"),
                    "questionType", prop("string", "type"),
                    "reviewStatus", prop("string", "review status"),
                    "limit", prop("integer", "max results")), null)),
            new Tool("get_question", "Get one question with options, answer and analysis.",
                    false, schema("object", Map.of("id", prop("string", "question UUID")), List.of("id"))),
            new Tool("analyze_exam", "Analyze a previously imported exam paper. Creates an AI-parsed draft.",
                    true, schema("object", Map.of("examId", prop("string", "exam paper UUID"),
                    "text", prop("string", "OCR text"), "questions", prop("array", "structured questions")),
                    List.of("examId"))),
            new Tool("generate_practice", "Generate a practice set as DRAFT. Requires teacher review.",
                    true, schema("object", Map.of("studentId", prop("string", "UUID"),
                    "subjectId", prop("string", "UUID"), "knowledgePointIds", prop("array", "UUID list"),
                    "questionCount", prop("integer", "count"), "mode", prop("string", "practice mode"),
                    "allowAi", prop("boolean", "allow AI originals")), List.of("studentId", "subjectId"))),
            new Tool("save_practice_draft", "Save a generated practice set as DRAFT (idempotent by request).",
                    true, schema("object", Map.of("studentId", prop("string", "UUID"),
                    "subjectId", prop("string", "UUID"), "knowledgePointIds", prop("array", "UUID list"),
                    "questionCount", prop("integer", "count")), List.of("studentId", "subjectId"))),
            new Tool("get_practice", "Get a practice set with items and current mastery.",
                    false, schema("object", Map.of("id", prop("string", "practice UUID")), List.of("id")))
    );
}
