package com.leslie.teachtrack.storage;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssetRepository {
    public record AssetRow(
            UUID id,
            String ownerType,
            UUID ownerId,
            String storageProvider,
            String storageKey,
            String mimeType,
            long sizeBytes,
            String sha256,
            String status
    ) {
    }

    private final JdbcTemplate jdbc;

    public AssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(AssetRow row) {
        jdbc.update("""
                insert into assets (id, owner_type, owner_id, storage_provider, storage_key,
                                    mime_type, size_bytes, sha256, visibility, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'PRIVATE', ?)
                """, row.id(), row.ownerType(), row.ownerId(), row.storageProvider(), row.storageKey(),
                row.mimeType(), row.sizeBytes(), row.sha256(), row.status());
    }

    public Optional<AssetRow> findById(UUID id) {
        return jdbc.query("""
                select id, owner_type, owner_id, storage_provider, storage_key, mime_type,
                       size_bytes, sha256, status
                from assets where id = ?
                """, (rs, rowNum) -> map(rs), id).stream().findFirst();
    }

    public Optional<AssetRow> findByKey(String key) {
        return jdbc.query("""
                select id, owner_type, owner_id, storage_provider, storage_key, mime_type,
                       size_bytes, sha256, status
                from assets where storage_key = ?
                """, (rs, rowNum) -> map(rs), key).stream().findFirst();
    }

    public void markReady(UUID id, long sizeBytes, String sha256) {
        jdbc.update("""
                update assets set status = 'READY', size_bytes = ?, sha256 = ?, updated_at = now()
                where id = ?
                """, sizeBytes, sha256, id);
    }

    public void markFailed(UUID id) {
        jdbc.update("update assets set status = 'FAILED', updated_at = now() where id = ?", id);
    }

    private static AssetRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AssetRow(
                rs.getObject("id", UUID.class),
                rs.getString("owner_type"),
                rs.getObject("owner_id", UUID.class),
                rs.getString("storage_provider"),
                rs.getString("storage_key"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("sha256"),
                rs.getString("status")
        );
    }
}
