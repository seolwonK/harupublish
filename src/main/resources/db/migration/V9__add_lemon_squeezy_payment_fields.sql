ALTER TABLE payments ADD COLUMN provider VARCHAR(40);
ALTER TABLE payments ADD COLUMN provider_checkout_id VARCHAR(120);
ALTER TABLE payments ADD COLUMN provider_checkout_url VARCHAR(1000);
ALTER TABLE payments ADD COLUMN provider_order_id VARCHAR(120);
ALTER TABLE payments ADD COLUMN provider_order_identifier VARCHAR(120);

CREATE INDEX idx_payments_provider_checkout ON payments (provider, provider_checkout_id);
CREATE INDEX idx_payments_provider_order ON payments (provider, provider_order_id);
