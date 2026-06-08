CREATE TABLE withdrawals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tutor_profile_id BIGINT NOT NULL,
    method VARCHAR(20) NOT NULL,
    requested_amount_usd DECIMAL(12, 2) NOT NULL,
    fee_rate DECIMAL(6, 4) NOT NULL,
    fee_amount_usd DECIMAL(12, 2) NOT NULL,
    net_payout_usd DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    promo_fee_waived BOOLEAN NOT NULL,
    promo_payback_pending BOOLEAN NOT NULL,
    promo_payback_amount_usd DECIMAL(12, 2),
    payout_reference VARCHAR(200),
    reject_reason VARCHAR(500),
    requested_at TIMESTAMP(6) NOT NULL,
    approved_at TIMESTAMP(6),
    paid_at TIMESTAMP(6),
    rejected_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_withdrawals_tutor_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id)
);

CREATE INDEX idx_withdrawals_tutor_created ON withdrawals (tutor_profile_id, created_at);
CREATE INDEX idx_withdrawals_status ON withdrawals (status);
