package com.leslie.teachtrack.system;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves which database the running backend is actually connected to.
 * Never returns credentials, only host/port/database and TLS state.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final JdbcTemplate jdbc;
    private final com.leslie.teachtrack.storage.ObjectStorageProvider storage;

    public SystemController(JdbcTemplate jdbc, com.leslie.teachtrack.storage.ObjectStorageProvider storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @GetMapping("/storage")
    public Map<String, Object> storage() {
        return Map.of("provider", storage.providerId());
    }

    @GetMapping("/database")
    public Map<String, Object> database() {
        Map<String, Object> row = jdbc.queryForMap("""
                select current_database() as database,
                       coalesce(inet_server_addr()::text, 'local-socket') as host,
                       coalesce(inet_server_port(), 0) as port,
                       current_setting('server_version') as version,
                       coalesce((select ssl from pg_stat_ssl where pid = pg_backend_pid()), false) as ssl,
                       current_user as db_user
                """);
        String host = String.valueOf(row.get("host"));
        String normalizedHost = host.contains("/") ? host.substring(0, host.indexOf('/')) : host;
        boolean local = "localhost".equalsIgnoreCase(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "local-socket".equals(normalizedHost);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engine", "postgresql");
        result.put("host", normalizedHost);
        result.put("port", row.get("port"));
        result.put("database", row.get("database"));
        result.put("user", row.get("db_user"));
        result.put("version", row.get("version"));
        result.put("ssl", row.get("ssl"));
        result.put("mode", local ? "LOCAL_DEV" : "CLOUD_OR_REMOTE");
        result.put("note", local
                ? "This is a local development database. Set TEACHTRACK_DB_URL to a managed PostgreSQL instance and restart with --spring.profiles.active=cloud."
                : "Connected to a remote PostgreSQL instance. Verify TLS and backups.");
        return result;
    }
}
