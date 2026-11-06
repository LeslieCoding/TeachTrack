package com.leslie.teachtrack.sync;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncChangeRepository {
    public record SyncChange(
            long seq,
            String entityType,
            UUID entityId,
            String operation,
            int version,
            String payload,
            Instant changedAt
    ) {
    }

    private final JdbcTemplate jdbc;

    public SyncChangeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(String entityType, UUID entityId, String operation, int version, UUID deviceId, String payloadJson) {
        jdbc.update("""
                insert into sync_changes (entity_type, entity_id, operation, version, device_id, payload)
                values (?, ?, ?, ?, ?, ?::jsonb)
                """, entityType, entityId, operation, version, deviceId, payloadJson);
    }

    public List<SyncChange> list(long cursor, int limit) {
        return jdbc.query("""
                select seq, entity_type, entity_id, operation, version, payload::text as payload, changed_at
                from sync_changes where seq > ? order by seq asc limit ?
                """, (rs, rowNum) -> new SyncChange(
                rs.getLong("seq"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("operation"),
                rs.getInt("version"),
                rs.getString("payload"),
                rs.getTimestamp("changed_at").toInstant()
        ), cursor, limit);
    }
}
