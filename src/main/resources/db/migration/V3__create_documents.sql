CREATE TABLE documents (
    document_id       UUID          PRIMARY KEY,
    knowledge_base_id UUID          NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    lesson_id         VARCHAR(80)   NOT NULL,
    lesson_title      VARCHAR(255)  NOT NULL,
    content_hash      VARCHAR(64)   NOT NULL,
    source_filename   VARCHAR(1000) NOT NULL,
    mime_type         VARCHAR(255)  NOT NULL,
    original_content  TEXT,
    content_blocks    JSONB         NOT NULL DEFAULT '[]',
    upload_source     VARCHAR(50)   NOT NULL,
    uploaded_by       UUID          NOT NULL REFERENCES users (user_id),
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_knowledge_base_id ON documents (knowledge_base_id);
CREATE INDEX idx_documents_content_hash      ON documents (content_hash);
CREATE INDEX idx_documents_uploaded_by       ON documents (uploaded_by);
CREATE INDEX idx_documents_status            ON documents (status);

-- One lesson per knowledge base; deduplication by lesson_id is enforced here.
CREATE UNIQUE INDEX idx_documents_kb_lesson ON documents (knowledge_base_id, lesson_id);
