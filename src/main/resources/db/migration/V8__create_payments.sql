CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    tutor_profile_id BIGINT NOT NULL,
    lesson_duration_minutes INT NOT NULL,
    lesson_pack_count INT NOT NULL,
    unit_amount DECIMAL(10,2) NOT NULL,
    subtotal_amount DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL,
    student_fee_amount DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    refund_reason VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_student_user
        FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_payments_tutor_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id)
);

CREATE INDEX idx_payments_student_created ON payments (student_user_id, created_at);
CREATE INDEX idx_payments_tutor_created ON payments (tutor_profile_id, created_at);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);
