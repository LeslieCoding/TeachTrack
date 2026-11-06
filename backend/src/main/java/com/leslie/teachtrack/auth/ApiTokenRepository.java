package com.leslie.teachtrack.auth;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApiTokenRepository {
    public record TokenRow(
            UUID tokenId,
            UUID userId,
            String displayName,
            String userStatus,
            List<String> scopes,
            Instant expiresAt,
            Instant revokedAt
    ) {
    }

    private final JdbcTemplate jdbc;

    public ApiTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TokenRow> findByHash(byte[] hash) {
        return jdbc.query("""
                select t.id, t.user_id, u.display_name, u.status, t.scopes, t.expires_at, t.revoked_at
                from api_tokens t join users u on u.id = t.user_id
                where t.token_hash = ?
                """, (rs, rowNum) -> new TokenRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("display_name"),
                rs.getString("status"),
                readScopes(rs.getArray("scopes")),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()
        ), hash).stream().findFirst();
    }

    public long count() {
        Long value = jdbc.queryForObject("select count(*) from api_tokens", Long.class);
        return value == null ? 0 : value;
    }

    public void touch(UUID tokenId) {
        jdbc.update("update api_tokens set last_used_at = now() where id = ?", tokenId);
    }

    public void insertUser(UUID userId, String displayName) {
        jdbc.update("insert into users (id, display_name, status) values (?, ?, 'ACTIVE')", userId, displayName);
    }

    public void insertRole(UUID userId, String role) {
        jdbc.update("insert into user_roles (user_id, role) values (?, ?) on conflict do nothing", userId, role);
    }

    public void insertToken(UUID tokenId, UUID userId, String name, String prefix, byte[] hash, List<String> scopes) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into api_tokens (id, user_id, name, token_prefix, token_hash, scopes)
                    values (?, ?, ?, ?, ?, ?)
                    """);
            statement.setObject(1, tokenId);
            statement.setObject(2, userId);
            statement.setString(3, name);
            statement.setString(4, prefix);
            statement.setBytes(5, hash);
            Array array = connection.createArrayOf("text", scopes.toArray());
            statement.setArray(6, array);
            return statement;
        });
    }

    private static List<String> readScopes(Array array) {
        try {
            if (array == null) {
                return List.of();
            }
            Object value = array.getArray();
            if (value instanceof String[] strings) {
                return List.of(strings);
            }
            if (value instanceof Object[] objects) {
                return java.util.Arrays.stream(objects).map(String::valueOf).toList();
            }
            return List.of();
        } catch (Exception exception) {
            return List.of();
        }
    }
}
