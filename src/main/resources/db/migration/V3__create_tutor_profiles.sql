CREATE TABLE tutor_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    display_name VARCHAR(100),
    short_introduction VARCHAR(255),
    about_me TEXT,
    what_i_offer TEXT,
    category VARCHAR(50),
    profile_image_url VARCHAR(500),
    intro_video_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    available_languages VARCHAR(255),
    lesson_price_amount DECIMAL(10, 2),
    available_time_note VARCHAR(500),
    payment_method VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMP(6),
    approved_at TIMESTAMP(6),
    rejected_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tutor_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_tutor_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_tutor_profiles_status ON tutor_profiles (status);
