-- Stores the per-step fingerprint records that enable resume-on-rerun semantics.
-- The composite PK (document_id, output_key) ensures one checkpoint per step per document.
CREATE TABLE step_checkpoints (
    document_id  UUID         NOT NULL REFERENCES documents (document_id) ON DELETE CASCADE,
    output_key   VARCHAR(100) NOT NULL,
    fingerprint  VARCHAR(16)  NOT NULL,
    completed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (document_id, output_key)
);
