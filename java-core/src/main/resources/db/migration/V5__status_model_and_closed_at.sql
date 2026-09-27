ALTER TABLE emergency_alerts
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;

ALTER TABLE service_requests
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;

UPDATE emergency_alerts SET status = 'SENT_TO_UK' WHERE status IN ('SENT', 'SUBMITTED');
UPDATE emergency_alerts SET status = 'IN_PROGRESS' WHERE status = 'ACK';
UPDATE emergency_alerts SET status = 'CLOSED' WHERE status = 'RESOLVED';

UPDATE service_requests SET status = 'SENT_TO_UK' WHERE status IN ('SENT', 'SUBMITTED');
UPDATE service_requests SET status = 'IN_PROGRESS' WHERE status = 'ACK';
UPDATE service_requests SET status = 'CLOSED' WHERE status = 'RESOLVED';

ALTER TABLE emergency_alerts ALTER COLUMN status SET DEFAULT 'NEW';
ALTER TABLE service_requests ALTER COLUMN status SET DEFAULT 'NEW';

CREATE INDEX IF NOT EXISTS idx_alerts_user_open
    ON emergency_alerts(user_id) WHERE status <> 'CLOSED';

CREATE INDEX IF NOT EXISTS idx_requests_user_open
    ON service_requests(user_id) WHERE status <> 'CLOSED';