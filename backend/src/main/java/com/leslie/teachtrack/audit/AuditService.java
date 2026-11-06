package com.leslie.teachtrack.audit;

import com.leslie.teachtrack.auth.AuthPrincipal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(
            AuthPrincipal principal,
            String action,
            String entityType,
            UUID entityId,
            String result,
            String errorCode
    ) {
        try {
            if (principal == null) {
                jdbc.update("""
                        insert into audit_logs (id, actor_type, actor_id, action, entity_type, entity_id, result, error_code)
                        values (?, 'SYSTEM', 'anonymous', ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), action, entityType, entityId, result, errorCode);
            } else {
                jdbc.update("""
                        insert into audit_logs (id, actor_type, actor_id, token_id, action, entity_type, entity_id, result, error_code)
                        values (?, 'USER', ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), principal.userId(), UUID.fromString(principal.tokenId()),
                        action, entityType, entityId, result, errorCode);
            }
        } catch (RuntimeException exception) {
            // Audit failure must never break the business path, but it must be visible.
            log.error("Unable to write audit log for action {} on {} {}", action, entityType, entityId, exception);
        }
    }

    public void recordAi(
            AuthPrincipal principal,
            String provider,
            String model,
            String requestId,
            String action,
            String entityType,
            UUID entityId,
            String result,
            String errorCode
    ) {
        try {
            jdbc.update("""
                    insert into audit_logs (id, actor_type, actor_id, token_id, provider, model, request_id,
                        action, entity_type, entity_id, result, error_code)
                    values (?, 'AI', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), principal == null ? "mcp" : principal.userId(),
                    principal == null ? null : UUID.fromString(principal.tokenId()),
                    provider, model, requestId, action, entityType, entityId, result, errorCode);
        } catch (RuntimeException exception) {
            log.error("Unable to write AI audit log for action {}", action, exception);
        }
    }
}
