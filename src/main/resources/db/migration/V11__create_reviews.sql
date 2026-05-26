CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT,
    tutor_profile_id BIGINT NOT NULL,
    student_user_id BIGINT,
    reviewer_name VARCHAR(100) NOT NULL,
    rating INT NOT NULL,
    body VARCHAR(1000) NOT NULL,
    visible BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_booking UNIQUE (booking_id),
    CONSTRAINT fk_reviews_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_reviews_tutor_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id),
    CONSTRAINT fk_reviews_student_user
        FOREIGN KEY (student_user_id) REFERENCES users (id)
);

CREATE INDEX idx_reviews_tutor_visible_created
    ON reviews (tutor_profile_id, visible, created_at);
