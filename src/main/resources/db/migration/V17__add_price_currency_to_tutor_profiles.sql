ALTER TABLE tutor_profiles ADD COLUMN price_currency VARCHAR(3);
ALTER TABLE tutor_profiles ADD COLUMN lesson_price_25_usd_amount DECIMAL(10, 2);
ALTER TABLE tutor_profiles ADD COLUMN lesson_price_50_usd_amount DECIMAL(10, 2);

UPDATE tutor_profiles SET price_currency = 'USD' WHERE price_currency IS NULL;
UPDATE tutor_profiles SET lesson_price_25_usd_amount = lesson_price_25_amount WHERE lesson_price_25_usd_amount IS NULL;
UPDATE tutor_profiles SET lesson_price_50_usd_amount = lesson_price_50_amount WHERE lesson_price_50_usd_amount IS NULL;
