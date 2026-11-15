-- Irin Ajo backend schema, version 1
-- Global, monotonically increasing change counter used as the sync cursor.
CREATE SEQUENCE change_seq START 1;

-- ============ Accounts ============
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    phone           VARCHAR(16) UNIQUE,
    google_sub      VARCHAR(255) UNIQUE,
    apple_sub       VARCHAR(255) UNIQUE,
    email           VARCHAR(320),
    role            VARCHAR(16)  NOT NULL DEFAULT 'USER',
    member_since    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE profiles (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(30),
    bio                 VARCHAR(160),
    city                VARCHAR(80),
    language            VARCHAR(3)   NOT NULL DEFAULT 'en',
    daily_goal_km       NUMERIC(3,1) NOT NULL DEFAULT 3.0,
    weight_kg           NUMERIC(5,1),
    weight_goal_kg_per_week NUMERIC(3,2),
    motivations         JSONB        NOT NULL DEFAULT '[]',
    region              VARCHAR(5)   NOT NULL DEFAULT 'south',
    voice_settings      JSONB        NOT NULL DEFAULT '{}',
    reminder_settings   JSONB        NOT NULL DEFAULT '{}',
    share_anonymised_walks BOOLEAN   NOT NULL DEFAULT FALSE,
    walk_safe_auto_after_dark BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_key          VARCHAR(255),
    cover_key           VARCHAR(255),
    updated_at          BIGINT       NOT NULL DEFAULT 0,   -- epoch ms from the phone (last write wins)
    seq                 BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE otp_challenges (
    phone           VARCHAR(16) PRIMARY KEY,
    code_hash       VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    last_sent_at    TIMESTAMPTZ NOT NULL,
    attempts        INT         NOT NULL DEFAULT 0,
    channel         VARCHAR(10) NOT NULL
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    device_id       VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);

-- ============ Journeys & keepsakes ============
CREATE TABLE journeys (
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id              VARCHAR(64)  NOT NULL,           -- made on the phone
    started_at      BIGINT,
    duration_sec    INT,
    distance_km     NUMERIC(7,2),
    steps           INT,
    time_of_day     VARCHAR(10),
    area            VARCHAR(120),
    landmarks       JSONB        NOT NULL DEFAULT '[]',
    custom_name     VARCHAR(40),
    polyline        TEXT,
    km_times        JSONB        NOT NULL DEFAULT '[]',
    felt_safe       BOOLEAN,
    start_lat       DOUBLE PRECISION,
    start_lng       DOUBLE PRECISION,
    updated_at      BIGINT       NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    geocoded        BOOLEAN      NOT NULL DEFAULT FALSE,
    seq             BIGINT       NOT NULL,
    PRIMARY KEY (user_id, id)
);
CREATE INDEX idx_journeys_user_seq ON journeys(user_id, seq);
CREATE INDEX idx_journeys_geocode ON journeys(geocoded) WHERE geocoded = FALSE AND deleted = FALSE;

CREATE TABLE keepsakes (
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    keepsake_id     VARCHAR(64) NOT NULL,
    earned_at       BIGINT      NOT NULL,           -- the phone's date is final
    seq             BIGINT      NOT NULL,
    PRIMARY KEY (user_id, keepsake_id)
);
CREATE INDEX idx_keepsakes_user_seq ON keepsakes(user_id, seq);

-- ============ Walk Safe ============
CREATE TABLE walk_safe_contacts (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(60) NOT NULL,
    phone           VARCHAR(16) NOT NULL,
    is_default      BOOLEAN     NOT NULL DEFAULT FALSE,
    link_token      VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_contacts_user ON walk_safe_contacts(user_id);
CREATE INDEX idx_contacts_phone ON walk_safe_contacts(phone);

CREATE TABLE walk_safe_sessions (
    id                  UUID PRIMARY KEY,
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_session_id   VARCHAR(64) NOT NULL,
    status              VARCHAR(10) NOT NULL,       -- ACTIVE, ENDED
    started_at          TIMESTAMPTZ NOT NULL,
    ended_at            TIMESTAMPTZ,
    last_point_at       TIMESTAMPTZ,
    distance_m          DOUBLE PRECISION NOT NULL DEFAULT 0,
    UNIQUE (user_id, client_session_id)
);
CREATE INDEX idx_sessions_user_started ON walk_safe_sessions(user_id, started_at DESC);

CREATE TABLE walk_safe_points (
    session_id      UUID        NOT NULL REFERENCES walk_safe_sessions(id) ON DELETE CASCADE,
    recorded_at     BIGINT      NOT NULL,           -- epoch ms
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (session_id, recorded_at)
);

-- ============ Push ============
CREATE TABLE push_tokens (
    token           VARCHAR(255) PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform        VARCHAR(10),
    device_id       VARCHAR(100),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_user ON push_tokens(user_id);

-- ============ Explore & safety ============
CREATE TABLE curated_walks (
    id              UUID PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    category        VARCHAR(20)  NOT NULL,          -- PARKS, CAFES, LANDMARKS, QUIET
    distance_km     NUMERIC(5,2) NOT NULL,
    est_minutes     INT          NOT NULL,
    difficulty      VARCHAR(10)  NOT NULL,          -- EASY, MODERATE, HARD
    safety_day      VARCHAR(10)  NOT NULL,          -- GOOD, FAIR, TAKE_CARE
    safety_night    VARCHAR(10)  NOT NULL,
    polyline        TEXT         NOT NULL,
    start_lat       DOUBLE PRECISION NOT NULL,
    start_lng       DOUBLE PRECISION NOT NULL,
    is_loop         BOOLEAN      NOT NULL DEFAULT TRUE,
    rated_on        DATE         NOT NULL DEFAULT CURRENT_DATE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_walks_start ON curated_walks(start_lat, start_lng);

-- Felt Safe feedback, aggregated per ~110 m grid cell and hour of day. No user ids stored.
CREATE TABLE safety_feedback (
    cell            VARCHAR(24) NOT NULL,
    hour            SMALLINT    NOT NULL,
    safe_count      INT         NOT NULL DEFAULT 0,
    unsafe_count    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (cell, hour)
);

-- ============ Content ============
CREATE TABLE tips (
    id              UUID PRIMARY KEY,
    language        VARCHAR(3)  NOT NULL,
    category        VARCHAR(30) NOT NULL,
    title           VARCHAR(120),
    body            TEXT        NOT NULL,
    version         BIGINT      NOT NULL,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_tips_version ON tips(version);

CREATE TABLE voice_packs (
    id              UUID PRIMARY KEY,
    speaker         VARCHAR(10) NOT NULL,           -- ada, tunde
    language        VARCHAR(3)  NOT NULL,           -- en, pcm
    version         INT         NOT NULL,
    object_key      VARCHAR(255) NOT NULL,
    size_bytes      BIGINT      NOT NULL,
    sha256          VARCHAR(64) NOT NULL,
    UNIQUE (speaker, language)
);

CREATE SEQUENCE content_version_seq START 1;
