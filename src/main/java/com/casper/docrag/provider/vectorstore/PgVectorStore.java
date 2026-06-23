package com.casper.docrag.provider.vectorstore;

import com.casper.docrag.domain.VectorStore;
import com.casper.docrag.domain.model.DocumentChunk;
import com.casper.docrag.domain.model.RetrievedChunk;
import com.casper.docrag.support.VectorLiterals;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * {@link VectorStore} 的 pgvector 實作（ADR-001/002）。
 *
 * <p>採客製 schema（document_chunks，見 §3.3）而非 Spring AI 內建的 vector_store 表，
 * 以保留 document_id 外鍵、chunk_index 與精確的相似度分數。檢索用 cosine 距離運算子
 * {@code <=>}（搭配 HNSW vector_cosine_ops 索引），similarity = 1 − distance。
 *
 * <p>門檻（防線 1）以 {@code distance <= 1 − minSimilarity} 在 SQL 內完成；
 * scope（防線 2）以 {@code document_id = :scope} 過濾。
 */
@Repository
public class PgVectorStore implements VectorStore {

    private static final String UPSERT_SQL = """
            INSERT INTO document_chunks (id, document_id, chunk_index, content, embedding)
            VALUES (?, ?, ?, ?, ?::vector)
            ON CONFLICT (document_id, chunk_index)
            DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public PgVectorStore(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DocumentChunk c = chunks.get(i);
                ps.setObject(1, c.id());
                ps.setObject(2, c.documentId());
                ps.setInt(3, c.chunkIndex());
                ps.setString(4, c.content());
                ps.setString(5, VectorLiterals.toLiteral(c.embedding()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    /** 刪除某文件的所有片段（重新處理前清空；正常刪文件由 FK ON DELETE CASCADE 處理）。 */
    public void deleteByDocumentId(UUID documentId) {
        jdbc.sql("DELETE FROM document_chunks WHERE document_id = ?").param(documentId).update();
    }

    @Override
    public List<RetrievedChunk> search(float[] queryVector, int topK, String documentScope, double minSimilarity) {
        String literal = VectorLiterals.toLiteral(queryVector);
        double maxDistance = 1.0 - minSimilarity;
        boolean scoped = documentScope != null && !documentScope.isBlank();

        String sql = "SELECT id, document_id, chunk_index, content, "
                + "1 - (embedding <=> (:qv)::vector) AS similarity "
                + "FROM document_chunks "
                + "WHERE (embedding <=> (:qv)::vector) <= :maxDistance "
                + (scoped ? "AND document_id = :scope " : "")
                + "ORDER BY embedding <=> (:qv)::vector "
                + "LIMIT :topK";

        JdbcClient.StatementSpec spec = jdbc.sql(sql)
                .param("qv", literal)
                .param("maxDistance", maxDistance)
                .param("topK", topK);
        if (scoped) {
            spec = spec.param("scope", UUID.fromString(documentScope.trim()));
        }

        return spec.query((rs, n) -> new RetrievedChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("similarity"))).list();
    }
}
