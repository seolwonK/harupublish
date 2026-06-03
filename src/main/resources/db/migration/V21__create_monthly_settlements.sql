CREATE TABLE monthly_settlements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tutor_profile_id BIGINT NOT NULL,
    settlement_year INT NOT NULL,
    settlement_month INT NOT NULL,
    gross_amount_usd DECIMAL(12, 2) NOT NULL,
    platform_fee_amount_usd DECIMAL(12, 2) NOT NULL,
    net_amount_usd DECIMAL(12, 2) NOT NULL,
    lesson_count INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_monthly_settlements_tutor_period UNIQUE (tutor_profile_id, settlement_year, settlement_month),
    CONSTRAINT fk_monthly_settlements_tutor_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id)
);

CREATE INDEX idx_monthly_settlements_tutor_period ON monthly_settlements (tutor_profile_id, settlement_year, settlement_month);
CREATE INDEX idx_monthly_settlements_status ON monthly_settlements (status);
