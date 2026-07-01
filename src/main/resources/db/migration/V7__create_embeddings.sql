CREATE TABLE content_embeddings (
    id                UUID        PRIMARY KEY,
    document_id       UUID        NOT NULL REFERENCES documents (document_id) ON DELETE CASCADE,
    knowledge_base_id UUID        NOT NULL,
    content           TEXT        NOT NULL,
    embedding         vector(1536),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_content_embeddings_document_id       ON content_embeddings (document_id);
CREATE INDEX idx_content_embeddings_knowledge_base_id ON content_embeddings (knowledge_base_id);
