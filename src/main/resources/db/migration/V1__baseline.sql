-- Pre-deployment baseline: V1–V7 squashed once at the wiki re-cut (see migrations.md).

CREATE TABLE users (
    user_id       UUID          PRIMARY KEY,
    display_name  VARCHAR(255)  NOT NULL,
    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255),
    avatar_url    VARCHAR(1000),
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email ON users (email);

CREATE TABLE knowledge_bases (
    kb_id       UUID         PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_knowledge_bases_owner_id ON knowledge_bases (owner_id);

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
    -- Deferred so a user delete can cascade through knowledge_bases before this is checked.
    uploaded_by       UUID          NOT NULL REFERENCES users (user_id) DEFERRABLE INITIALLY DEFERRED,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Deduplication is per knowledge base; conversation turns are never deduplicated.
CREATE UNIQUE INDEX uq_documents_kb_content_hash ON documents (knowledge_base_id, content_hash)
    WHERE upload_source <> 'CONVERSATION';
-- Several documents may share a lesson: each is one version of it.
CREATE INDEX idx_documents_kb_lesson    ON documents (knowledge_base_id, lesson_id);
CREATE INDEX idx_documents_uploaded_by ON documents (uploaded_by);
