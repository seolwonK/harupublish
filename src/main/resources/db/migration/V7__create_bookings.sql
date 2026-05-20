CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    tutor_profile_id BIGINT NOT NULL,
    schedule_slot_id BIGINT NOT NULL,
    lesson_duration_minutes INT NOT NULL,
    start_at TIMESTAMP(6) NOT NULL,
    end_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    cancel_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bookings_schedule_slot UNIQUE (schedule_slot_id),
    CONSTRAINT fk_bookings_student_user
        FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_tutor_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id),
    CONSTRAINT fk_bookings_schedule_slot
        FOREIGN KEY (schedule_slot_id) REFERENCES tutor_schedule_slots (id)
);

CREATE INDEX idx_bookings_student_start ON bookings (student_user_id, start_at);
CREATE INDEX idx_bookings_tutor_start ON bookings (tutor_profile_id, start_at);
CREATE INDEX idx_bookings_status_start ON bookings (status, start_at);
