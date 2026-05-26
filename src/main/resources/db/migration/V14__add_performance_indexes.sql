CREATE INDEX idx_tutor_profiles_status_hidden_approved
    ON tutor_profiles (status, hidden, approved_at);

CREATE INDEX idx_payments_lesson_credit
    ON payments (student_user_id, tutor_profile_id, lesson_duration_minutes, status);

CREATE INDEX idx_bookings_lesson_consumed
    ON bookings (student_user_id, tutor_profile_id, lesson_duration_minutes, status);
