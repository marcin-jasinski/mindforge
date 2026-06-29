CREATE TABLE knowledge_bases (
    kb_id          UUID         PRIMARY KEY,
    owner_id       UUID         NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    document_count INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_knowledge_bases_owner_id ON knowledge_bases (owner_id);
