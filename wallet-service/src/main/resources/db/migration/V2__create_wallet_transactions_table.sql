CREATE TYPE transaction_type AS ENUM ('CREDIT', 'DEBIT');

CREATE TABLE wallet_transactions (
    id           UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id    UUID             NOT NULL REFERENCES wallets(id),
    type         transaction_type NOT NULL,
    amount       NUMERIC(19,4)    NOT NULL,
    balance_after NUMERIC(19,4)   NOT NULL,
    reference    VARCHAR(255),
    created_at   TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_transactions_wallet_id ON wallet_transactions (wallet_id);
