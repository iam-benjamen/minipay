CREATE TYPE wallet_type AS ENUM ('PERSONAL', 'SAVINGS', 'BUSINESS');
CREATE TYPE wallet_status AS ENUM ('ACTIVE', 'FROZEN', 'CLOSED');
CREATE TYPE currency_code AS ENUM ('NGN', 'USD', 'GBP', 'EUR');

CREATE TABLE wallets (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID          NOT NULL,
    type       wallet_type   NOT NULL,
    currency   currency_code NOT NULL DEFAULT 'NGN',
    balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    status     wallet_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_type_currency UNIQUE (user_id, type, currency)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);
