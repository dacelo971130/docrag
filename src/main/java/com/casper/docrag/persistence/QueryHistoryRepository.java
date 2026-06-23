package com.casper.docrag.persistence;

import com.casper.docrag.domain.model.QueryRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** query_history 表的存取（JdbcClient）。 */
@Repository
public class QueryHistoryRepository {

    private final JdbcClient jdbc;

    public QueryHistoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(QueryRecord r) {
        String idsLiteral = (r.retrievedChunkIds() == null || r.retrievedChunkIds().isEmpty())
                ? null
                : "{" + r.retrievedChunkIds().stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";

        jdbc.sql("""
                        INSERT INTO query_history
                          (id, question, answer, document_scope, retrieved_chunk_ids, grounded,
                           token_cost, latency_ms, embedding_ms, retrieval_ms, llm_ms)
                        VALUES (?, ?, ?, ?, ?::uuid[], ?, ?, ?, ?, ?, ?)
                        """)
                .params(r.id(), r.question(), r.answer(), r.documentScope(), idsLiteral, r.grounded(),
                        r.tokenCost(), r.latencyMs(), r.embeddingMs(), r.retrievalMs(), r.llmMs())
                .update();
    }

    /** 查詢歷史列表（SPEC §3.2 回傳 question / answer / timestamp，其餘欄位省略）。 */
    public List<QueryRecord> findRecent(int limit) {
        return jdbc.sql("SELECT id, question, answer, created_at FROM query_history ORDER BY created_at DESC LIMIT ?")
                .param(limit)
                .query((rs, n) -> new QueryRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("question"),
                        rs.getString("answer"),
                        null, null, null, null, null, null, null, null,
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
