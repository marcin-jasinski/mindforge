-- Study material cut from the wiki (ADR 0018, T21, T27). Nothing here is ever read by export.

CREATE TABLE flashcards (
    knowledge_base_id UUID              NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    card_id           CHAR(16)          NOT NULL,
    -- No FK to wiki_pages: a deleted page's cards come back with a revert of the deletion.
    page_id           UUID              NOT NULL,
    section_anchor    VARCHAR(80),
    card_type         VARCHAR(10)       NOT NULL CHECK (card_type IN ('BASIC', 'CLOZE', 'REVERSE')),
    front             TEXT              NOT NULL,
    back              TEXT              NOT NULL,
    source_hash       CHAR(16)          NOT NULL,
    ease_factor       DOUBLE PRECISION  NOT NULL DEFAULT 2.5,
    interval_days     INT               NOT NULL DEFAULT 0,
    repetitions       INT               NOT NULL DEFAULT 0,
    due_at            TIMESTAMPTZ       NOT NULL,
    retired_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    PRIMARY KEY (knowledge_base_id, card_id)
);

CREATE INDEX idx_flashcards_kb_page ON flashcards (knowledge_base_id, page_id);
CREATE INDEX idx_flashcards_kb_due  ON flashcards (knowledge_base_id, due_at) WHERE retired_at IS NULL;

CREATE TABLE study_events (
    event_id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    knowledge_base_id UUID         NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    page_id           UUID         NOT NULL,
    card_id           CHAR(16),
    kind              VARCHAR(4)   NOT NULL CHECK (kind IN ('CARD', 'QUIZ')),
    score             SMALLINT     NOT NULL CHECK (score BETWEEN 0 AND 5),
    occurred_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_study_events_kb_page_time ON study_events (knowledge_base_id, page_id, occurred_at DESC);

CREATE TABLE quiz_sessions (
    session_id        UUID         PRIMARY KEY,
    knowledge_base_id UUID         NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    -- Deferred: a user delete removes both sides through knowledge_bases (T24).
    user_id           UUID         NOT NULL REFERENCES users (user_id) DEFERRABLE INITIALLY DEFERRED,
    -- Reference answers and grounding excerpts: server-only.
    questions         JSONB        NOT NULL,
    cursor            INT          NOT NULL DEFAULT 0,
    expires_at        TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_quiz_sessions_kb         ON quiz_sessions (knowledge_base_id);
CREATE INDEX idx_quiz_sessions_user       ON quiz_sessions (user_id);
CREATE INDEX idx_quiz_sessions_expires_at ON quiz_sessions (expires_at);
