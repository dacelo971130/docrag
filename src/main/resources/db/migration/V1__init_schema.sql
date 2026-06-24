CREATE SCHEMA IF NOT EXISTS docrag;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE docrag.documents (
                               id UUID PRIMARY KEY,
                               name TEXT NOT NULL,
                               status TEXT NOT NULL CHECK (status IN ('PENDING','PROCESSING','READY','FAILED')),
                               chunk_count INT NOT NULL DEFAULT 0,
                               error TEXT,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE docrag.document_chunks (
                                     id UUID PRIMARY KEY,
                                     document_id UUID NOT NULL REFERENCES docrag.documents(id) ON DELETE CASCADE,
                                     chunk_index INT NOT NULL,
                                     content TEXT NOT NULL,
                                     embedding vector(${embedding_dimension}) NOT NULL,
                                     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                     UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_document_id
    ON docrag.document_chunks (document_id);

CREATE INDEX idx_document_chunks_embedding
    ON docrag.document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE docrag.query_history (
                                   id UUID PRIMARY KEY,
                                   question TEXT NOT NULL,
                                   answer TEXT,
                                   document_scope UUID REFERENCES docrag.documents(id),
                                   retrieved_chunk_ids UUID[],
                                   grounded BOOLEAN,
                                   token_cost INT,
                                   latency_ms INT,
                                   embedding_ms INT,
                                   retrieval_ms INT,
                                   llm_ms INT,
                                   created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_query_history_created_at
    ON docrag.query_history (created_at DESC);