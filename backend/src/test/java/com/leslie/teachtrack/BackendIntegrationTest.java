package com.leslie.teachtrack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
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
class BackendIntegrationTest {
    private static final String DB_URL = System.getenv().getOrDefault(
            "TEACHTRACK_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55432/teachtrack_test");
    private static final String DB_USER = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_USER", "teachtrack");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("TEACHTRACK_TEST_DB_PASSWORD", "");
    private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("teachtrack.auth.bootstrap-token", () -> BOOTSTRAP_TOKEN);
        registry.add("teachtrack.storage.provider", () -> "local");
        registry.add("teachtrack.storage.local-root", () -> "build/test-storage");
        registry.add("teachtrack.storage.secret", () -> "test-storage-secret");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Test
    void flywayIsRepeatable() {
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void healthIsPublic() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"UP\""));
    }

    @Test
    void apiRequiresToken() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/students", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void listsStudentsWithToken() {
        ResponseEntity<Map> response = rest.exchange("/api/v1/students?q=__no_such_student__", HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(List.of(), response.getBody().get("items"));
    }

    @Test
    void databaseModeReportsLocalDevelopment() {
        ResponseEntity<Map> response = rest.exchange("/api/v1/system/database", HttpMethod.GET,
                new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("postgresql", response.getBody().get("engine"));
        assertEquals("LOCAL_DEV", response.getBody().get("mode"));
        assertEquals(false, response.getBody().get("ssl"));
    }

    @Test
    void privateUploadAndSignedDownload() {
        Map<String, Object> body = Map.of(
                "ownerType", "students",
                "filename", "exam.png",
                "contentType", "image/png",
                "sizeBytes", 4
        );
        ResponseEntity<Map> upload = rest.exchange("/api/v1/assets/upload-url", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), Map.class);
        assertEquals(HttpStatus.OK, upload.getStatusCode());
        String uploadUrl = String.valueOf(upload.getBody().get("uploadUrl"));

        byte[] payload = new byte[] {1, 2, 3, 4};
        HttpHeaders binary = new HttpHeaders();
        binary.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity<Void> put = rest.exchange(URI.create(uploadUrl), HttpMethod.PUT,
                new HttpEntity<>(payload, binary), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, put.getStatusCode());

        String assetId = String.valueOf(upload.getBody().get("assetId"));
        ResponseEntity<Map> downloadUrl = rest.exchange("/api/v1/assets/" + assetId + "/download-url",
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertEquals(HttpStatus.OK, downloadUrl.getStatusCode());
        String signed = String.valueOf(downloadUrl.getBody().get("url"));

        ResponseEntity<byte[]> downloaded = rest.exchange(URI.create(signed), HttpMethod.GET,
                HttpEntity.EMPTY, byte[].class);
        assertEquals(HttpStatus.OK, downloaded.getStatusCode());
        assertEquals(4, downloaded.getBody().length);

        // A tampered signature must fail.
        ResponseEntity<byte[]> tampered = rest.exchange(URI.create(signed.replaceAll("sig=.*", "sig=bad")),
                HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
        assertEquals(HttpStatus.FORBIDDEN, tampered.getStatusCode());
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(BOOTSTRAP_TOKEN);
        return headers;
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
