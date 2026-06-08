CREATE TABLE tutor_earning_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tutor_profile_id BIGINT NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    booking_id BIGINT,
    gross_amount_usd DECIMAL(12, 2),
    platform_fee_amount_usd DECIMAL(12, 2),
    net_amount_usd DECIMAL(12, 2) NOT NULL,
    balance_after_usd DECIMAL(12, 2) NOT NULL,
    applied_platform_fee_rate DECIMAL(6, 4),
    memo VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tutor_earning_ledger_tutor_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id),
    CONSTRAINT fk_tutor_earning_ledger_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

CREATE INDEX idx_tutor_earning_ledger_tutor_created ON tutor_earning_ledger (tutor_profile_id, created_at);
CREATE INDEX idx_tutor_earning_ledger_booking ON tutor_earning_ledger (booking_id);
