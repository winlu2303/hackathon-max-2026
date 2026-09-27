CREATE TABLE buildings (
    id BIGSERIAL PRIMARY KEY,
    address TEXT NOT NULL,
    fias_id VARCHAR(64) UNIQUE,
    uk_name VARCHAR(255),
    max_chat_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    max_user_id BIGINT UNIQUE,
    role VARCHAR(32) NOT NULL,
    building_id BIGINT REFERENCES buildings(id),
    apartment VARCHAR(16),
    name VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE emergency_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    building_id BIGINT REFERENCES buildings(id),
    type VARCHAR(32) NOT NULL,
    description TEXT,
    photo_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'SENT',
    dispatcher_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ack_at TIMESTAMP,
    dispatched_at TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE TABLE service_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    building_id BIGINT REFERENCES buildings(id),
    category VARCHAR(32) NOT NULL,
    subtype VARCHAR(64),
    description TEXT,
    photo_url TEXT,
    preferred_time VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    master_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP,
    registered_at TIMESTAMP,
    response_due_at TIMESTAMP
);

CREATE INDEX idx_alerts_building_status ON emergency_alerts(building_id, status);
CREATE INDEX idx_requests_master_status ON service_requests(master_id, status);
CREATE INDEX idx_users_max_id ON users(max_user_id);

INSERT INTO buildings (address, fias_id, uk_name)
VALUES ('г. Казань, ул. Баумана, д. 15', '16-77-010101-123', 'ООО «УК Пример»');

INSERT INTO users (max_user_id, role, building_id, apartment, name) VALUES
    (1000001, 'RESIDENT', 1, '42', 'Тестовый житель'),
    (1000002, 'DISPATCHER', 1, NULL, 'Диспетчер'),
    (1000003, 'MASTER', 1, NULL, 'Сантехник Иван');