package com.leslie.teachtrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExamApiTest {
    private static final String DB_URL = System.getenv().getOrDefault(
            "TEACHTRACK_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55432/teachtrack_test");
    private static final String DB_USER = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_USER", "teachtrack");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_PASSWORD", "");
    private static final String TOKEN = "test-bootstrap-token";
    private static final String SUBJECT_ID = "5681f185-310d-52e4-964c-be9d8cf1f5a6";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("teachtrack.auth.bootstrap-token", () -> TOKEN);
        registry.add("teachtrack.storage.provider", () -> "local");
        registry.add("teachtrack.storage.local-root", () -> "build/test-storage");
        registry.add("teachtrack.storage.secret", () -> "test-storage-secret");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void importAnalyzeCorrectApprove() {
        UUID studentId = UUID.randomUUID();
        jdbc.update("""
                insert into students (id, legacy_id, display_name, status, subject_id, name_completeness)
                values (?, ?, '试卷测试学生', 'ACTIVE', ?, 'CONFIRMED')
                """, studentId, "exam-student-" + studentId, UUID.fromString(SUBJECT_ID));
        UUID knowledgeId = UUID.randomUUID();
        jdbc.update("""
                insert into knowledge_points (id, subject_id, name, code, grade)
                values (?, ?, '液体压强', ?, '八年级')
                """, knowledgeId, UUID.fromString(SUBJECT_ID), "EXAM-KP-" + knowledgeId);
        jdbc.update("""
                insert into course_sessions (id, legacy_id, student_id, date, lesson_title, lesson_content)
                values (?, ?, ?, '2026-09-01', '液体压强复习', '液体压强公式与实验')
                """, UUID.randomUUID(), "exam-course-" + studentId, studentId);

        Map<String, Object> importBody = Map.of(
                "studentId", studentId.toString(),
                "subjectId", SUBJECT_ID,
                "examName", "十月月考",
                "examDate", "2026-10-20",
                "fullScore", 6,
                "score", 3
        );
        ResponseEntity<Map> imported = rest.exchange("/api/v1/exams/import", HttpMethod.POST,
                new HttpEntity<>(importBody, headers()), Map.class);
        assertEquals(HttpStatus.CREATED, imported.getStatusCode(), String.valueOf(imported.getBody()));
        String paperId = String.valueOf(imported.getBody().get("id"));

        Map<String, Object> question1 = Map.of(
                "number", "1", "type", "SINGLE_CHOICE", "score", 2, "fullScore", 2,
                "studentAnswer", "A", "standardAnswer", "A", "stem", "液体压强选择题"
        );
        Map<String, Object> question2 = Map.of(
                "number", "2", "type", "CALCULATION", "score", 1, "fullScore", 4,
                "studentAnswer", "", "standardAnswer", "p=ρgh", "stem", "液体压强计算题"
        );
        ResponseEntity<Map> analyzed = rest.exchange("/api/v1/exams/" + paperId + "/analyze",
                HttpMethod.POST, new HttpEntity<>(Map.of("questions", List.of(question1, question2)), headers()),
                Map.class);
        assertEquals(HttpStatus.OK, analyzed.getStatusCode(), String.valueOf(analyzed.getBody()));
        assertEquals("AI_PARSED", ((Map) analyzed.getBody().get("paper")).get("analysisStatus"));
        assertEquals(2, ((List<?>) analyzed.getBody().get("questions")).size());
        assertEquals(2, ((List<?>) analyzed.getBody().get("analyses")).size());

        Map analysis1 = (Map) ((List<?>) analyzed.getBody().get("analyses")).get(0);
        Map analysis2 = (Map) ((List<?>) analyzed.getBody().get("analyses")).get(1);
        assertEquals(true, analysis1.get("taughtBefore"));
        assertEquals("UNKNOWN", analysis1.get("mistakeType"));
        assertEquals(true, analysis2.get("taughtBefore"));
        assertEquals("UNKNOWN", analysis2.get("mistakeType"));
        String report = String.valueOf(analyzed.getBody().get("reportJson"));
        assertTrue(report.contains("questionCount"));
        assertTrue(report.contains("wrongCount"));

        String examQuestion2 = String.valueOf(((Map) ((List<?>) analyzed.getBody().get("questions")).get(1)).get("id"));
        ResponseEntity<Map> corrected = rest.exchange(
                "/api/v1/exams/" + paperId + "/questions/" + examQuestion2, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("score", 3, "teacherComment", "步骤不完整",
                        "mistakeType", "CALCULATION_ERROR", "mistakeReason", "单位换算错误"), headers()),
                Map.class);
        assertEquals(HttpStatus.OK, corrected.getStatusCode(), String.valueOf(corrected.getBody()));
        Map correctedAnalysis = (Map) ((List<?>) corrected.getBody().get("analyses")).get(1);
        assertEquals("CALCULATION_ERROR", correctedAnalysis.get("mistakeType"));

        ResponseEntity<Map> approved = rest.exchange("/api/v1/exams/" + paperId + "/approve?importToBank=true",
                HttpMethod.POST, new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, approved.getStatusCode(), String.valueOf(approved.getBody()));
        assertEquals("APPROVED", ((Map) approved.getBody().get("paper")).get("analysisStatus"));
        Integer linked = jdbc.queryForObject("""
                select count(*) from exam_questions where exam_paper_id = ? and question_id is not null
                """, Integer.class, UUID.fromString(paperId));
        assertEquals(2, linked);

        ResponseEntity<List> exams = rest.exchange("/api/v1/students/" + studentId + "/exams",
                HttpMethod.GET, new HttpEntity<>(headers()), List.class);
        assertEquals(HttpStatus.OK, exams.getStatusCode());
        assertNotNull(exams.getBody());
        assertEquals(1, exams.getBody().size());
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
