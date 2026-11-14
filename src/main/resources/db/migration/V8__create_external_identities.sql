-- Chat-platform identities linked to accounts (Phase 18). A user delete removes its links.

CREATE TABLE external_identities (
    platform    VARCHAR(50)  NOT NULL,
    external_id VARCHAR(64)  NOT NULL,
    user_id     UUID         NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (platform, external_id)
);

CREATE INDEX idx_external_identities_user_id ON external_identities (user_id);
