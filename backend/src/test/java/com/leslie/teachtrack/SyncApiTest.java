package com.leslie.teachtrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class SyncApiTest {
    private static final String DB_URL = System.getenv().getOrDefault(
            "TEACHTRACK_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55432/teachtrack_test");
    private static final String DB_USER = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_USER", "teachtrack");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_PASSWORD", "");
    private static final String TOKEN = "test-bootstrap-token";

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
    void pushIsIdempotentAndDetectsVersionConflicts() {
        String legacy = "sync-test-" + UUID.randomUUID();
        String operationId = UUID.randomUUID().toString();

        Map<String, Object> create = Map.of(
                "operationId", operationId,
                "entityType", "student",
                "entityId", legacy,
                "baseVersion", 0,
                "payload", Map.of(
                        "legacyId", legacy,
                        "displayName", "同步测试学生",
                        "status", "ACTIVE",
                        "school", "测试学校"
                )
        );
        ResponseEntity<Map> first = push(List.of(create));
        assertEquals(HttpStatus.OK, first.getStatusCode(), String.valueOf(first.getBody()));
        assertEquals(1, ((List<?>) first.getBody().get("applied")).size());
        assertEquals(0, ((List<?>) first.getBody().get("conflicts")).size());

        // Same operation id: idempotent, no second row and no conflict.
        ResponseEntity<Map> replay = push(List.of(create));
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals(1, ((List<?>) replay.getBody().get("applied")).size());

        String entityId = String.valueOf(((Map) ((List<?>) first.getBody().get("applied")).get(0)).get("entityId"));
        Map<String, Object> update = Map.of(
                "operationId", UUID.randomUUID().toString(),
                "entityType", "student",
                "entityId", entityId,
                "baseVersion", 1,
                "payload", Map.of("displayName", "同步测试学生 v2")
        );
        ResponseEntity<Map> updated = push(List.of(update));
        assertEquals(HttpStatus.OK, updated.getStatusCode());
        assertEquals(2, ((Map) ((List<?>) updated.getBody().get("applied")).get(0)).get("version"));

        Map<String, Object> stale = Map.of(
                "operationId", UUID.randomUUID().toString(),
                "entityType", "student",
                "entityId", entityId,
                "baseVersion", 1,
                "payload", Map.of("displayName", "不应覆盖")
        );
        ResponseEntity<Map> conflict = push(List.of(stale));
        assertEquals(HttpStatus.OK, conflict.getStatusCode());
        assertEquals(1, ((List<?>) conflict.getBody().get("conflicts")).size());
        Map conflictBody = (Map) ((List<?>) conflict.getBody().get("conflicts")).get(0);
        assertEquals(1, conflictBody.get("baseVersion"));
        assertEquals(2, conflictBody.get("currentVersion"));
        assertTrue(((List<?>) conflictBody.get("choices")).contains("KEEP_LOCAL"));

        Map<String, Object> force = Map.of(
                "operationId", UUID.randomUUID().toString(),
                "entityType", "student",
                "entityId", entityId,
                "baseVersion", 2,
                "force", true,
                "payload", Map.of("displayName", "强制本地覆盖")
        );
        ResponseEntity<Map> forced = push(List.of(force));
        assertEquals(HttpStatus.OK, forced.getStatusCode());
        assertEquals(3, ((Map) ((List<?>) forced.getBody().get("applied")).get(0)).get("version"));

        ResponseEntity<Map> changes = rest.exchange("/api/v1/sync/changes?cursor=0&limit=50",
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, changes.getStatusCode());
        assertTrue(((Number) changes.getBody().get("nextCursor")).longValue() > 0);
    }

    private ResponseEntity<Map> push(List<Map<String, Object>> operations) {
        Map<String, Object> body = Map.of(
                "deviceId", UUID.randomUUID().toString(),
                "operations", operations
        );
        return rest.exchange("/api/v1/sync/operations", HttpMethod.POST,
                new HttpEntity<>(body, headers()), Map.class);
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
