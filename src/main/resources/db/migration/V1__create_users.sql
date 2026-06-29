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
