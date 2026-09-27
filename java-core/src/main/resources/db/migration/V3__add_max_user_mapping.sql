CREATE TABLE IF NOT EXISTS max_users (
    id          BIGSERIAL PRIMARY KEY,
    chat_id     BIGINT NOT NULL UNIQUE,
    user_id     BIGINT,
    phone       VARCHAR(32),
    role        VARCHAR(32),
    address     VARCHAR(512),
    building_id BIGINT,
    uk_id       BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_max_users_chat_id ON max_users(chat_id);
CREATE INDEX IF NOT EXISTS idx_max_users_user_id ON max_users(user_id);
CREATE INDEX IF NOT EXISTS idx_max_users_phone   ON max_users(phone);

ALTER TABLE management_companies
    ADD COLUMN IF NOT EXISTS inn             VARCHAR(16),
    ADD COLUMN IF NOT EXISTS ogrn            VARCHAR(20),
    ADD COLUMN IF NOT EXISTS region          VARCHAR(256),
    ADD COLUMN IF NOT EXISTS city            VARCHAR(256),
    ADD COLUMN IF NOT EXISTS address         VARCHAR(512),
    ADD COLUMN IF NOT EXISTS phone           TEXT,
    ADD COLUMN IF NOT EXISTS email           TEXT,
    ADD COLUMN IF NOT EXISTS website         TEXT,
    ADD COLUMN IF NOT EXISTS telegram        TEXT,
    ADD COLUMN IF NOT EXISTS whatsapp        TEXT,
    ADD COLUMN IF NOT EXISTS vk              TEXT,
    ADD COLUMN IF NOT EXISTS ok              TEXT,
    ADD COLUMN IF NOT EXISTS instagram       TEXT,
    ADD COLUMN IF NOT EXISTS max_link        TEXT,
    ADD COLUMN IF NOT EXISTS category        VARCHAR(256),
    ADD COLUMN IF NOT EXISTS description     TEXT,
    ADD COLUMN IF NOT EXISTS branches        INTEGER,
    ADD COLUMN IF NOT EXISTS working_hours   VARCHAR(256);

CREATE INDEX IF NOT EXISTS idx_mc_region ON management_companies(region);
CREATE INDEX IF NOT EXISTS idx_mc_city   ON management_companies(city);
CREATE INDEX IF NOT EXISTS idx_mc_inn    ON management_companies(inn);

ALTER TABLE emergency_alerts
    ADD COLUMN IF NOT EXISTS address     VARCHAR(512),
    ADD COLUMN IF NOT EXISTS apartment   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS caller_name VARCHAR(128);

ALTER TABLE service_requests
    ADD COLUMN IF NOT EXISTS address     VARCHAR(512),
    ADD COLUMN IF NOT EXISTS apartment   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS caller_name VARCHAR(128);