ALTER TABLE tutor_profiles
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE AFTER payment_method;

CREATE INDEX idx_tutor_profiles_status_hidden ON tutor_profiles (status, hidden);