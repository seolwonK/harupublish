ALTER TABLE tutor_earning_ledger ADD COLUMN withdrawal_id BIGINT;
ALTER TABLE tutor_earning_ledger ADD COLUMN idempotency_key VARCHAR(120);

ALTER TABLE tutor_earning_ledger
    ADD CONSTRAINT uk_tutor_earning_ledger_idempotency UNIQUE (idempotency_key);

CREATE INDEX idx_tutor_earning_ledger_withdrawal ON tutor_earning_ledger (withdrawal_id);
