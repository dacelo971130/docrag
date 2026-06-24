-- 方案 A — 初始 schema（SPEC §3.3）。
-- embedding 維度由 Flyway placeholder ${embedding_dimension} 注入，
-- 對齊所選 embedding 模型（OpenAI=1536 / Ollama nomic-embed-text=768）。

CREATE EXTENSION IF NOT EXISTS vector;

-- ===== 文件主表 =====
CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    name        TEXT NOT NULL,
    status      TEXT NOT NULL,                 -- PENDING / PROCESSING / READY / FAILED
    chunk_count INT  NOT NULL DEFAULT 0,
    error       TEXT,                          -- 失敗原因（錯誤透明，SPEC §7）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ===== 文件片段 + 向量 =====
CREATE TABLE document_chunks (
    id          UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT  NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(${embedding_dimension}) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_document_id ON document_chunks (document_id);

-- 向量相似度索引：HNSW + cosine（ADR-002）
CREATE INDEX idx_document_chunks_embedding
    ON document_chunks USING hnsw (embedding vector_cosine_ops);

-- ===== 查詢歷史（含延遲分解與成本，M3/M4） =====
CREATE TABLE query_history (
    id                  UUID PRIMARY KEY,
    question            TEXT NOT NULL,
    answer              TEXT,
    document_scope      UUID,                  -- 限定的文件範圍（null = 全部）
    retrieved_chunk_ids UUID[],
    grounded            BOOLEAN,               -- 是否有檢索命中（防線 1 結果）
    token_cost          INT,
    latency_ms          INT,                   -- 端到端
    embedding_ms        INT,                   -- 分解：向量化
    retrieval_ms        INT,                   -- 分解：檢索
    llm_ms              INT,                   -- 分解：生成
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_history_created_at ON query_history (created_at DESC);
