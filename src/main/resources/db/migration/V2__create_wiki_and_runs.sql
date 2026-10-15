-- The wiki, its history and the ingest runs that write it (ADRs 0014, 0015).
-- An FK whose two sides are removed by the same cascade is DEFERRABLE INITIALLY DEFERRED, so a knowledge-base or
-- user delete never depends on the order cascades fire (T24).

CREATE TABLE ingest_runs (
    run_id               UUID         PRIMARY KEY,
    knowledge_base_id    UUID         NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    kind                 VARCHAR(10)  NOT NULL CHECK (kind IN ('INGEST', 'REVERT', 'LINT')),
    document_id          UUID         REFERENCES documents (document_id) DEFERRABLE INITIALLY DEFERRED,
    reverts_run_id       UUID         REFERENCES ingest_runs (run_id) DEFERRABLE INITIALLY DEFERRED,
    status               VARCHAR(10)  NOT NULL
        CHECK (status IN ('QUEUED', 'RUNNING', 'WRITTEN', 'COMPLETED', 'FAILED')),
    attempt              SMALLINT     NOT NULL DEFAULT 1,
    failure_reason       TEXT,
    retryable            BOOLEAN,
    failures             JSONB        NOT NULL DEFAULT '[]',
    supersession_skipped BOOLEAN      NOT NULL DEFAULT FALSE,
    supersession_count   INT          NOT NULL DEFAULT 0,
    step_versions        JSONB        NOT NULL DEFAULT '{}',
    findings             JSONB        NOT NULL DEFAULT '[]',
    -- Never in an API response; NULL means not measured.
    cost                 NUMERIC(12, 6),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at           TIMESTAMPTZ,
    finished_at          TIMESTAMPTZ
);

CREATE INDEX idx_ingest_runs_kb_status_created ON ingest_runs (knowledge_base_id, status, created_at);
CREATE INDEX idx_ingest_runs_document_id       ON ingest_runs (document_id);
CREATE INDEX idx_ingest_runs_reverts_run_id    ON ingest_runs (reverts_run_id);

-- The lease: at most one active run per knowledge base.
ALTER TABLE knowledge_bases
    ADD COLUMN active_run_id UUID REFERENCES ingest_runs (run_id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE wiki_pages (
    page_id           UUID          PRIMARY KEY,
    knowledge_base_id UUID          NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    path              VARCHAR(89)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    description       VARCHAR(300)  NOT NULL,
    page_type         VARCHAR(50)   NOT NULL,
    markdown_body     TEXT          NOT NULL,
    revision          INT           NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_wiki_pages_kb_path UNIQUE (knowledge_base_id, path),
    CONSTRAINT uq_wiki_pages_kb_page UNIQUE (knowledge_base_id, page_id)
);

-- Derived from bodies. The composite key keeps a link inside its page's knowledge base.
CREATE TABLE page_links (
    link_id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    knowledge_base_id UUID         NOT NULL,
    source_page_id    UUID         NOT NULL,
    target_path       VARCHAR(89)  NOT NULL,
    fragment          VARCHAR(80),
    CONSTRAINT fk_page_links_source_page FOREIGN KEY (knowledge_base_id, source_page_id)
        REFERENCES wiki_pages (knowledge_base_id, page_id) ON DELETE CASCADE
);

CREATE INDEX idx_page_links_kb_target_path ON page_links (knowledge_base_id, target_path);
CREATE INDEX idx_page_links_kb_source_page ON page_links (knowledge_base_id, source_page_id);

-- History outlives pages: page_revisions, page_sources and page_supersessions have no FK to wiki_pages.

CREATE TABLE page_revisions (
    page_id           UUID          NOT NULL,
    revision          INT           NOT NULL,
    knowledge_base_id UUID          NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    ingest_run_id     UUID          NOT NULL REFERENCES ingest_runs (run_id) DEFERRABLE INITIALLY DEFERRED,
    -- A page's path never changes; it is kept here because a deleted page has no row to take it from.
    path              VARCHAR(89)   NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    description       VARCHAR(300)  NOT NULL,
    page_type         VARCHAR(50)   NOT NULL,
    markdown_body     TEXT,         -- NULL is a tombstone
    created_at        TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (page_id, revision),
    -- One revision per page per run, so a run's pre-run state is always revision r - 1.
    CONSTRAINT uq_page_revisions_page_run UNIQUE (page_id, ingest_run_id)
);

CREATE INDEX idx_page_revisions_kb_run ON page_revisions (knowledge_base_id, ingest_run_id);
CREATE INDEX idx_page_revisions_run    ON page_revisions (ingest_run_id);

CREATE TABLE page_sources (
    page_id           UUID  NOT NULL,
    ingest_run_id     UUID  NOT NULL REFERENCES ingest_runs (run_id) DEFERRABLE INITIALLY DEFERRED,
    knowledge_base_id UUID  NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    document_id       UUID  NOT NULL REFERENCES documents (document_id) DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (page_id, ingest_run_id)
);

CREATE INDEX idx_page_sources_kb_document ON page_sources (knowledge_base_id, document_id);
CREATE INDEX idx_page_sources_run         ON page_sources (ingest_run_id);
CREATE INDEX idx_page_sources_document    ON page_sources (document_id);

CREATE TABLE page_supersessions (
    supersession_id     UUID         PRIMARY KEY,
    knowledge_base_id   UUID         NOT NULL REFERENCES knowledge_bases (kb_id) ON DELETE CASCADE,
    superseded_page_id  UUID         NOT NULL,
    section_anchor      VARCHAR(80)  NOT NULL,
    superseding_page_id UUID         NOT NULL,
    ingest_run_id       UUID         NOT NULL REFERENCES ingest_runs (run_id) DEFERRABLE INITIALLY DEFERRED,
    created_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_page_supersessions_kb_superseded  ON page_supersessions (knowledge_base_id, superseded_page_id);
CREATE INDEX idx_page_supersessions_kb_superseding ON page_supersessions (knowledge_base_id, superseding_page_id);
CREATE INDEX idx_page_supersessions_run            ON page_supersessions (ingest_run_id);
