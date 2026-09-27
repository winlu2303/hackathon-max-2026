ALTER TABLE emergency_alerts
    ADD COLUMN IF NOT EXISTS uk_id BIGINT;

ALTER TABLE service_requests
    ADD COLUMN IF NOT EXISTS uk_id BIGINT;

UPDATE emergency_alerts e
SET uk_id = u.uk_id
FROM max_users u
WHERE e.user_id = u.user_id AND e.uk_id IS NULL;

UPDATE service_requests r
SET uk_id = u.uk_id
FROM max_users u
WHERE r.user_id = u.user_id AND r.uk_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_alerts_uk ON emergency_alerts(uk_id);
CREATE INDEX IF NOT EXISTS idx_requests_uk ON service_requests(uk_id);