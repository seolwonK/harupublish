CREATE TABLE promo_fee_waiver_grants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tutor_profile_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    waiver_until DATE,
    granted_by_admin_id BIGINT,
    revoked_by_admin_id BIGINT,
    granted_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_promo_fee_waiver_grants_tutor UNIQUE (tutor_profile_id),
    CONSTRAINT fk_promo_fee_waiver_grants_tutor_profile
        FOREIGN KEY (tutor_profile_id) REFERENCES tutor_profiles (id)
);

CREATE INDEX idx_promo_fee_waiver_grants_active ON promo_fee_waiver_grants (active);
