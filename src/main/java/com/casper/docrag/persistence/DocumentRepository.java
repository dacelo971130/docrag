package com.casper.docrag.persistence;

import com.casper.docrag.domain.model.Document;
import com.casper.docrag.domain.model.DocumentStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** documents 表的存取（JdbcClient）。 */
@Repository
public class DocumentRepository {

    private static final int MAX_ERROR_LEN = 4000;

    private static final RowMapper<Document> MAPPER = (rs, n) -> new Document(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            DocumentStatus.valueOf(rs.getString("status")),
            rs.getInt("chunk_count"),
            rs.getString("error"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPending(UUID id, String name) {
        jdbc.sql("INSERT INTO documents (id, name, status, chunk_count) VALUES (?, ?, ?, 0)")
                .params(id, name, DocumentStatus.PENDING.name())
                .update();
    }

    public void markProcessing(UUID id) {
        jdbc.sql("UPDATE documents SET status = ? WHERE id = ?")
                .params(DocumentStatus.PROCESSING.name(), id)
                .update();
    }

    public void markReady(UUID id, int chunkCount) {
        jdbc.sql("UPDATE documents SET status = ?, chunk_count = ?, error = NULL WHERE id = ?")
                .params(DocumentStatus.READY.name(), chunkCount, id)
                .update();
    }

    public void markFailed(UUID id, String error) {
        jdbc.sql("UPDATE documents SET status = ?, error = ? WHERE id = ?")
                .params(DocumentStatus.FAILED.name(), truncate(error), id)
                .update();
    }

    public Optional<Document> findById(UUID id) {
        return jdbc.sql("SELECT * FROM documents WHERE id = ?").param(id).query(MAPPER).optional();
    }

    public List<Document> findAll() {
        return jdbc.sql("SELECT * FROM documents ORDER BY created_at DESC").query(MAPPER).list();
    }

    public boolean existsById(UUID id) {
        return jdbc.sql("SELECT 1 FROM documents WHERE id = ?").param(id)
                .query(Integer.class).optional().isPresent();
    }

    public boolean deleteById(UUID id) {
        return jdbc.sql("DELETE FROM documents WHERE id = ?").param(id).update() > 0;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LEN ? error : error.substring(0, MAX_ERROR_LEN);
    }
}
