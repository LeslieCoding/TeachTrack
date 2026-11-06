package com.leslie.teachtrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leslie.teachtrack.auth.TokenService;
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
class McpApiTest {
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

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TokenService tokenService;

    @Test
    void initializeListToolsAndReadTool() {
        ResponseEntity<Map> init = post(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "initialize",
                "params", Map.of("protocolVersion", "2024-11-05")
        ), headers(TOKEN));
        assertEquals(HttpStatus.OK, init.getStatusCode());
        assertEquals("2.0", init.getBody().get("jsonrpc"));
        assertEquals("teachtrack", ((Map) ((Map) init.getBody().get("result")).get("serverInfo")).get("name"));

        ResponseEntity<Map> list = post(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"), headers(TOKEN));
        assertEquals(HttpStatus.OK, list.getStatusCode());
        List tools = (List) ((Map) list.getBody().get("result")).get("tools");
        assertEquals(11, tools.size());
        List<String> names = tools.stream().map(tool -> String.valueOf(((Map) tool).get("name"))).toList();
        assertTrue(names.contains("search_questions"));
        assertFalse(names.contains("delete_student"));
        assertFalse(names.contains("database_reset"));

        ResponseEntity<Map> call = post(Map.of(
                "jsonrpc", "2.0", "id", 3, "method", "tools/call",
                "params", Map.of("name", "search_students", "arguments", Map.of("query", "__none__"))
        ), headers(TOKEN));
        assertEquals(HttpStatus.OK, call.getStatusCode());
        assertFalse(((Boolean) ((Map) call.getBody().get("result")).get("isError")));

        Integer aiAudit = jdbc.queryForObject(
                "select count(*) from audit_logs where actor_type = 'AI' and action = 'mcp.search_students'",
                Integer.class);
        assertTrue(aiAudit != null && aiAudit > 0);
    }

    @Test
    void readOnlyTokenCannotCreateDraft() {
        UUID userId = jdbc.queryForObject("select user_id from api_tokens limit 1", UUID.class);
        String raw = "read-only-" + UUID.randomUUID();
        jdbc.update("""
                insert into api_tokens (id, user_id, name, token_prefix, token_hash, scopes)
                values (?, ?, 'read-only', ?, ?, '{ai.read}'::text[])
                """, UUID.randomUUID(), userId, raw.substring(0, 11), tokenService.hash(raw));

        ResponseEntity<Map> call = post(Map.of(
                "jsonrpc", "2.0", "id", 4, "method", "tools/call",
                "params", Map.of("name", "generate_practice", "arguments", Map.of(
                        "studentId", UUID.randomUUID().toString(),
                        "subjectId", "5681f185-310d-52e4-964c-be9d8cf1f5a6"
                ))
        ), headers(raw));
        assertEquals(HttpStatus.OK, call.getStatusCode());
        Map error = (Map) call.getBody().get("error");
        assertTrue(String.valueOf(error.get("message")).contains("ai.write.draft"));
    }

    @Test
    void mcpRequiresToken() {
        ResponseEntity<String> response = rest.postForEntity("/mcp", Map.of("jsonrpc", "2.0", "id", 1,
                "method", "tools/list"), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private ResponseEntity<Map> post(Map<String, Object> body, HttpHeaders headers) {
        return rest.exchange("/mcp", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
