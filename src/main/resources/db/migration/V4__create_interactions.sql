-- Query conversations (Phase 11, T28).

CREATE TABLE interactions (
    interaction_id    UUID         PRIMARY KEY,
    knowledge_base_id UUID         NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    -- Deferred: a user delete removes both sides through knowledge_bases (T24).
    user_id           UUID         NOT NULL REFERENCES users (user_id) DEFERRABLE INITIALLY DEFERRED,
    started_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_interactions_kb_user ON interactions (knowledge_base_id, user_id);
CREATE INDEX idx_interactions_user    ON interactions (user_id);

CREATE TABLE interaction_turns (
    turn_id           UUID         PRIMARY KEY,
    knowledge_base_id UUID         NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    interaction_id    UUID         NOT NULL REFERENCES interactions (interaction_id) ON DELETE CASCADE,
    question          TEXT         NOT NULL,
    answer            TEXT         NOT NULL,
    -- Paths, not ids: a path is a page's stable identity.
    used_page_paths   TEXT[]       NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_interaction_turns_kb_interaction ON interaction_turns (knowledge_base_id, interaction_id, created_at);
