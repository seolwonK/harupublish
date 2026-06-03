ALTER TABLE bookings ADD COLUMN completion_state VARCHAR(30);
ALTER TABLE bookings ADD COLUMN settled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN settled_at TIMESTAMP(6);
ALTER TABLE bookings ADD COLUMN earning_amount_usd DECIMAL(12, 2);

CREATE INDEX idx_bookings_settled_end ON bookings (settled, end_at);
