CREATE TABLE haru_credit_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance_usd DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    last_activity_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_haru_credit_accounts_user UNIQUE (user_id),
    CONSTRAINT fk_haru_credit_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE haru_credit_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    amount_usd DECIMAL(12, 2) NOT NULL,
    balance_after_usd DECIMAL(12, 2) NOT NULL,
    payment_id BIGINT,
    idempotency_key VARCHAR(120),
    memo VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_haru_credit_ledger_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_haru_credit_ledger_account
        FOREIGN KEY (account_id) REFERENCES haru_credit_accounts (id)
);

CREATE INDEX idx_haru_credit_ledger_account_created ON haru_credit_ledger (account_id, created_at);
CREATE INDEX idx_haru_credit_ledger_payment ON haru_credit_ledger (payment_id);
