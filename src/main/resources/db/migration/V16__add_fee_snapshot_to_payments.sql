ALTER TABLE payments ADD COLUMN applied_student_fee_rate DECIMAL(6,4);
ALTER TABLE payments ADD COLUMN applied_five_pack_discount_rate DECIMAL(6,4);
ALTER TABLE payments ADD COLUMN applied_ten_pack_discount_rate DECIMAL(6,4);
ALTER TABLE payments ADD COLUMN settings_version INT;
ALTER TABLE payments ADD COLUMN display_currency VARCHAR(3);
ALTER TABLE payments ADD COLUMN fx_rate_used DECIMAL(18,8);
ALTER TABLE payments ADD COLUMN fx_rate_source VARCHAR(40);
ALTER TABLE payments ADD COLUMN fx_captured_at TIMESTAMP(6);
