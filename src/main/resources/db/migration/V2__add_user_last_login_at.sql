ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMP(6) NULL AFTER updated_at;
