ALTER TABLE bookings ADD COLUMN jitsi_provider VARCHAR(30);
ALTER TABLE bookings ADD COLUMN jitsi_room_name VARCHAR(160);
ALTER TABLE bookings ADD COLUMN jitsi_created_at TIMESTAMP(6);

CREATE INDEX idx_bookings_jitsi_room_name ON bookings (jitsi_room_name);
