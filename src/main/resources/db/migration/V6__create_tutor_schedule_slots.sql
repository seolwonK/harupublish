CREATE TABLE tutor_schedule_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tutor_profile_id BIGINT NOT NULL,
    start_at TIMESTAMP(6) NOT NULL,
    end_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tutor_schedule_slots_profile_start UNIQUE (tutor_profile_id, start_at),
    CONSTRAINT fk_tutor_schedule_slots_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id)
);

CREATE INDEX idx_tutor_schedule_slots_profile_start
    ON tutor_schedule_slots (tutor_profile_id, start_at);
