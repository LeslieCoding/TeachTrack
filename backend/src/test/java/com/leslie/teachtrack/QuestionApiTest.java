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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuestionApiTest {
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

    @Autowired
    private TestRestTemplate rest;

    @Test
    void questionLifecycleSearchReviewAndOptimisticLock() {
        String stem = "测试题 " + UUID.randomUUID() + " 关于液体压强";
        Map<String, Object> knowledge = Map.of(
                "knowledgePointId", createKnowledgePoint(),
                "importance", "PRIMARY"
        );
        Map<String, Object> create = Map.of(
                "subjectId", SUBJECT_ID,
                "grade", "八年级",
                "questionType", "SINGLE_CHOICE",
                "difficulty", 2,
                "stem", stem,
                "sourceType", "SELF_CREATED",
                "options", List.of(Map.of("key", "A", "content", "选项A"),
                        Map.of("key", "B", "content", "选项B")),
                "answer", Map.of("standardAnswer", "A", "analysis", "解析"),
                "knowledge", List.of(knowledge),
                "abilityTags", List.of("理解")
        );
        ResponseEntity<Map> created = rest.exchange("/api/v1/questions", HttpMethod.POST,
                new HttpEntity<>(create, headers()), Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
        Map question = (Map) created.getBody().get("question");
        String id = String.valueOf(question.get("id"));
        assertEquals(1, question.get("version"));

        ResponseEntity<Map> duplicate = rest.exchange("/api/v1/questions", HttpMethod.POST,
                new HttpEntity<>(create, headers()), Map.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("DUPLICATE_QUESTION", ((Map) duplicate.getBody().get("error")).get("code"));

        ResponseEntity<Map> found = rest.exchange("/api/v1/questions?q=" + stem, HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, found.getStatusCode());
        assertTrue(((Number) found.getBody().get("total")).intValue() >= 1);
        assertNotNull(found.getBody().get("items"));

        Map<String, Object> update = new java.util.HashMap<>();
        update.put("baseVersion", 1);
        update.put("stem", stem + "（修订）");
        update.put("difficulty", 3);
        ResponseEntity<Map> updated = rest.exchange("/api/v1/questions/" + id, HttpMethod.PATCH,
                new HttpEntity<>(update, headers()), Map.class);
        assertEquals(HttpStatus.OK, updated.getStatusCode(), String.valueOf(updated.getBody()));
        assertEquals(2, ((Map) updated.getBody().get("question")).get("version"));

        ResponseEntity<Map> stale = rest.exchange("/api/v1/questions/" + id, HttpMethod.PATCH,
                new HttpEntity<>(update, headers()), Map.class);
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals("VERSION_CONFLICT", ((Map) stale.getBody().get("error")).get("code"));

        ResponseEntity<Map> approved = rest.exchange("/api/v1/questions/" + id + "/review", HttpMethod.POST,
                new HttpEntity<>(Map.of("status", "APPROVED"), headers()), Map.class);
        assertEquals(HttpStatus.OK, approved.getStatusCode());
        assertEquals("APPROVED", ((Map) approved.getBody().get("question")).get("reviewStatus"));

        ResponseEntity<Map> versions = rest.exchange("/api/v1/questions/" + id, HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, versions.getStatusCode());

        ResponseEntity<Void> deleted = rest.exchange("/api/v1/questions/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers()), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());

        ResponseEntity<Map> gone = rest.exchange("/api/v1/questions/" + id, HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.NOT_FOUND, gone.getStatusCode());
    }

    @Test
    void imageUploadListAndSignedDownload() {
        String stem = "题图接口测试 " + UUID.randomUUID();
        ResponseEntity<Map> created = rest.exchange("/api/v1/questions", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "subjectId", SUBJECT_ID,
                        "grade", "八年级",
                        "questionType", "EXPERIMENT",
                        "difficulty", 3,
                        "stem", stem,
                        "sourceType", "SELF_CREATED"
                ), headers()), Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
        String id = String.valueOf(((Map) created.getBody().get("question")).get("id"));

        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setBearerAuth(TOKEN);
        imageHeaders.setContentType(MediaType.IMAGE_PNG);
        byte[] image = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
        ResponseEntity<Map> uploaded = rest.exchange("/api/v1/questions/" + id + "/image", HttpMethod.POST,
                new HttpEntity<>(image, imageHeaders), Map.class);
        assertEquals(HttpStatus.CREATED, uploaded.getStatusCode(), String.valueOf(uploaded.getBody()));
        assertNotNull(uploaded.getBody().get("assetId"));

        ResponseEntity<List> listed = rest.exchange("/api/v1/questions/" + id + "/images", HttpMethod.GET,
                new HttpEntity<>(headers()), List.class);
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        assertEquals(1, listed.getBody().size());
        String url = String.valueOf(((Map) listed.getBody().get(0)).get("url"));
        ResponseEntity<byte[]> downloaded = rest.getForEntity(java.net.URI.create(url), byte[].class);
        assertEquals(HttpStatus.OK, downloaded.getStatusCode());
        assertEquals(image.length, downloaded.getBody().length);
        assertEquals(MediaType.IMAGE_PNG, downloaded.getHeaders().getContentType());
    }

    @Test
    void bulkReviewAndDeleteReportPartialResults() {
        List<String> ids = List.of(createQuestion("批量审核测试一 " + UUID.randomUUID()),
                createQuestion("批量审核测试二 " + UUID.randomUUID()));
        ResponseEntity<Map> reviewed = rest.exchange("/api/v1/questions/bulk-review", HttpMethod.POST,
                new HttpEntity<>(Map.of("ids", ids, "status", "APPROVED"), headers()), Map.class);
        assertEquals(HttpStatus.OK, reviewed.getStatusCode(), String.valueOf(reviewed.getBody()));
        assertEquals(2, ((Number) reviewed.getBody().get("succeeded")).intValue());
        assertEquals(0, ((List) reviewed.getBody().get("failed")).size());

        ResponseEntity<Map> deleted = rest.exchange("/api/v1/questions/bulk-delete", HttpMethod.POST,
                new HttpEntity<>(Map.of("ids", ids), headers()), Map.class);
        assertEquals(HttpStatus.OK, deleted.getStatusCode(), String.valueOf(deleted.getBody()));
        assertEquals(2, ((Number) deleted.getBody().get("succeeded")).intValue());
        for (String id : ids) {
            ResponseEntity<Map> gone = rest.exchange("/api/v1/questions/" + id, HttpMethod.GET,
                    new HttpEntity<>(headers()), Map.class);
            assertEquals(HttpStatus.NOT_FOUND, gone.getStatusCode());
        }
    }

    private String createQuestion(String stem) {
        ResponseEntity<Map> created = rest.exchange("/api/v1/questions", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "subjectId", SUBJECT_ID,
                        "grade", "八年级",
                        "questionType", "OTHER",
                        "difficulty", 3,
                        "stem", stem,
                        "sourceType", "SELF_CREATED"
                ), headers()), Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
        return String.valueOf(((Map) created.getBody().get("question")).get("id"));
    }

    private String createKnowledgePoint() {
        String name = "液体压强-" + UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "subjectId", SUBJECT_ID,
                "grade", "八年级",
                "name", name
        );
        ResponseEntity<Map> response = rest.exchange("/api/v1/knowledge-points", HttpMethod.POST,
                new HttpEntity<>(body, headers()), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), String.valueOf(response.getBody()));
        return String.valueOf(response.getBody().get("id"));
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
