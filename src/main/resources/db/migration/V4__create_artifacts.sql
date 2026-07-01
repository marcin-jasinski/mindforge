CREATE TABLE artifacts (
    artifact_id          UUID        PRIMARY KEY,
    document_id          UUID        NOT NULL UNIQUE REFERENCES documents (document_id) ON DELETE CASCADE,
    knowledge_base_id    UUID        NOT NULL,
    summary_text         TEXT,
    summary_key_points   JSONB       NOT NULL DEFAULT '[]',
    flashcards           JSONB       NOT NULL DEFAULT '[]',
    concept_map          JSONB,
    quiz_questions       JSONB       NOT NULL DEFAULT '[]',
    relevance_passed     BOOLEAN,
    relevance_reason     TEXT,
    relevance_confidence REAL,
    completed_step       VARCHAR(100),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_artifacts_knowledge_base_id ON artifacts (knowledge_base_id);
