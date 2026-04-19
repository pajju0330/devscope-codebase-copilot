-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE repos (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    url         TEXT,
    status      TEXT        NOT NULL DEFAULT 'PENDING',
    error       TEXT,
    ingested_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE code_chunks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id     UUID        NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    file_path   TEXT        NOT NULL,
    class_name  TEXT,
    method_name TEXT,
    language    TEXT,
    start_line  INT,
    end_line    INT,
    content     TEXT        NOT NULL,
    embedding   vector(1536),
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX code_chunks_repo_id_idx ON code_chunks(repo_id);
CREATE INDEX code_chunks_embedding_idx ON code_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE TABLE dependency_edges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repo_id         UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    caller_file     TEXT,
    caller_class    TEXT,
    caller_method   TEXT,
    callee_class    TEXT,
    callee_method   TEXT
);

CREATE INDEX dependency_edges_repo_id_idx ON dependency_edges(repo_id);
CREATE INDEX dependency_edges_caller_idx  ON dependency_edges(caller_class, caller_method);
CREATE INDEX dependency_edges_callee_idx  ON dependency_edges(callee_class, callee_method);
