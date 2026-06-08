ALTER TABLE payments ADD COLUMN refund_credit_amount_usd DECIMAL(10, 2);
ALTER TABLE payments ADD COLUMN refund_approved_at TIMESTAMP(6);
