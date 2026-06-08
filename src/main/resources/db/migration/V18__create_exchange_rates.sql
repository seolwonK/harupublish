CREATE TABLE exchange_rates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    source VARCHAR(40) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    valid_until TIMESTAMP(6),
    PRIMARY KEY (id)
);

CREATE INDEX idx_exchange_rates_pair_captured ON exchange_rates (base_currency, quote_currency, captured_at);

INSERT INTO exchange_rates (base_currency, quote_currency, rate, source, captured_at, valid_until)
VALUES ('USD', 'KRW', 1380.00000000, 'SEED', CURRENT_TIMESTAMP(6), NULL);
