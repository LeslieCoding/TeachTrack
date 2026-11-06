package com.leslie.teachtrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leslie.teachtrack.common.Ids;
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
class PracticeApiTest {
    private static final String DB_URL = System.getenv().getOrDefault(
            "TEACHTRACK_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55432/teachtrack_test");
    private static final String DB_USER = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_USER", "teachtrack");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_PASSWORD", "");
    private static final String TOKEN = "test-bootstrap-token";
    private static final UUID SUBJECT_ID = UUID.fromString("5681f185-310d-52e4-964c-be9d8cf1f5a6");

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
    void generateApproveSubmitAndUpdateMastery() {
        UUID studentId = UUID.randomUUID();
        jdbc.update("""
                insert into students (id, legacy_id, display_name, status, subject_id, name_completeness)
                values (?, ?, '练习测试学生', 'ACTIVE', ?, 'CONFIRMED')
                """, studentId, "practice-student-" + studentId, SUBJECT_ID);
        UUID knowledgeId = insertKnowledge("练习知识点");
        UUID question1 = insertQuestion("练习题目一 " + UUID.randomUUID(), "A", knowledgeId, 3);
        UUID question2 = insertQuestion("练习题目二 " + UUID.randomUUID(), "A", knowledgeId, 3);

        ResponseEntity<Map> generated = rest.exchange("/api/v1/practices/generate", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "studentId", studentId.toString(),
                        "subjectId", SUBJECT_ID.toString(),
                        "knowledgePointIds", List.of(knowledgeId.toString()),
                        "questionCount", 2,
                        "minDifficulty", 1,
                        "maxDifficulty", 5,
                        "totalScore", 10,
                        "allowAi", false,
                        "mode", "BASIC"
                ), headers()), Map.class);
        assertEquals(HttpStatus.CREATED, generated.getStatusCode(), String.valueOf(generated.getBody()));
        Map set = (Map) generated.getBody().get("set");
        String practiceId = String.valueOf(set.get("id"));
        assertEquals("DRAFT", set.get("status"));
        List items = (List) generated.getBody().get("items");
        assertEquals(2, items.size());
        assertEquals(5.0, ((Number) ((Map) items.get(0)).get("score")).doubleValue());

        ResponseEntity<Map> approved = rest.exchange("/api/v1/practices/" + practiceId + "/approve",
                HttpMethod.POST, new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, approved.getStatusCode());

        String item1 = String.valueOf(((Map) items.get(0)).get("id"));
        String item2 = String.valueOf(((Map) items.get(1)).get("id"));
        ResponseEntity<Map> submitted = rest.exchange("/api/v1/practices/" + practiceId + "/submit",
                HttpMethod.POST, new HttpEntity<>(Map.of("answers", List.of(
                        Map.of("itemId", item1, "studentAnswer", "A"),
                        Map.of("itemId", item2, "studentAnswer", "B")
                )), headers()), Map.class);
        assertEquals(HttpStatus.OK, submitted.getStatusCode(), String.valueOf(submitted.getBody()));
        assertEquals("COMPLETED", ((Map) submitted.getBody().get("set")).get("status"));
        List mastery = (List) submitted.getBody().get("mastery");
        assertFalse(mastery.isEmpty());
        Map masteryRow = (Map) mastery.get(0);
        assertTrue(((Number) masteryRow.get("confidence")).doubleValue() > 0);
        assertTrue(((Number) masteryRow.get("masteryScore")).doubleValue() >= 0);

        ResponseEntity<List> masteryApi = rest.exchange("/api/v1/students/" + studentId + "/mastery",
                HttpMethod.GET, new HttpEntity<>(headers()), List.class);
        assertEquals(HttpStatus.OK, masteryApi.getStatusCode());
        assertFalse(masteryApi.getBody().isEmpty());

        ResponseEntity<Map> pdf = rest.exchange("/api/v1/practices/" + practiceId + "/export?variant=STUDENT&format=PDF",
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, pdf.getStatusCode(), String.valueOf(pdf.getBody()));
        String pdfUrl = String.valueOf(pdf.getBody().get("downloadUrl"));
        ResponseEntity<byte[]> pdfBytes = rest.getForEntity(java.net.URI.create(pdfUrl), byte[].class);
        assertEquals(HttpStatus.OK, pdfBytes.getStatusCode());
        assertEquals("%PDF", new String(pdfBytes.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        ResponseEntity<Map> docx = rest.exchange("/api/v1/practices/" + practiceId + "/export?variant=TEACHER&format=DOCX",
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, docx.getStatusCode(), String.valueOf(docx.getBody()));
        String docxUrl = String.valueOf(docx.getBody().get("downloadUrl"));
        ResponseEntity<byte[]> docxBytes = rest.getForEntity(java.net.URI.create(docxUrl), byte[].class);
        assertEquals(HttpStatus.OK, docxBytes.getStatusCode());
        assertEquals("PK", new String(docxBytes.getBody(), 0, 2, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    void insufficientApprovedQuestionsIsReported() {
        UUID studentId = UUID.randomUUID();
        jdbc.update("""
                insert into students (id, legacy_id, display_name, status, subject_id, name_completeness)
                values (?, ?, '无题测试学生', 'ACTIVE', ?, 'CONFIRMED')
                """, studentId, "empty-student-" + studentId, SUBJECT_ID);
        UUID knowledgeId = insertKnowledge("无题目知识点");
        ResponseEntity<Map> response = rest.exchange("/api/v1/practices/generate", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "studentId", studentId.toString(),
                        "subjectId", SUBJECT_ID.toString(),
                        "knowledgePointIds", List.of(knowledgeId.toString()),
                        "questionCount", 3,
                        "allowAi", false,
                        "mode", "BASIC"
                ), headers()), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INSUFFICIENT_QUESTIONS", ((Map) response.getBody().get("error")).get("code"));
    }

    private UUID insertKnowledge(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into knowledge_points (id, subject_id, name, code, grade)
                values (?, ?, ?, ?, '八年级')
                """, id, SUBJECT_ID, name, "PRACTICE-KP-" + id);
        return id;
    }

    private UUID insertQuestion(String stem, String answer, UUID knowledgeId, int difficulty) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into questions (id, subject_id, question_type, difficulty, stem, stem_hash,
                    normalized_hash, source_type, review_status, copyright_status, usage_scope, version)
                values (?, ?, 'SINGLE_CHOICE', ?, ?, ?, ?, 'SELF_CREATED', 'APPROVED',
                    'PERSONAL_USE', 'PRIVATE_TEACHING', 1)
                """, id, SUBJECT_ID, difficulty, stem, Ids.sha256Hex(stem),
                stem.toLowerCase().replaceAll("\\s+", ""));
        jdbc.update("""
                insert into question_answers (question_id, standard_answer)
                values (?, ?)
                """, id, answer);
        jdbc.update("""
                insert into question_knowledge (question_id, knowledge_point_id, importance)
                values (?, ?, 'PRIMARY')
                """, id, knowledgeId);
        return id;
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
