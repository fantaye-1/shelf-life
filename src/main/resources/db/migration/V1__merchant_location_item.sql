-- Core tenant and catalog tables. Every row below merchant is scoped by merchant_id.

CREATE TABLE merchant (
    id                      UUID          PRIMARY KEY,
    square_merchant_id      VARCHAR(64)   UNIQUE,
    name                    VARCHAR(255)  NOT NULL,
    -- Filled in by the Square connect flow (M6). Stored encrypted, never in plain text.
    encrypted_access_token  VARCHAR(4096),
    encrypted_refresh_token VARCHAR(4096),
    token_expires_at        TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL
);

CREATE TABLE location (
    id                 UUID          PRIMARY KEY,
    merchant_id        UUID          NOT NULL REFERENCES merchant (id),
    square_location_id VARCHAR(64),
    name               VARCHAR(255)  NOT NULL,
    -- IANA zone id, e.g. America/New_York. "Expires today" is computed in this zone.
    timezone           VARCHAR(64)   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_location_square_id UNIQUE (merchant_id, square_location_id)
);

CREATE INDEX ix_location_merchant ON location (merchant_id);

CREATE TABLE item (
    id                  UUID          PRIMARY KEY,
    merchant_id         UUID          NOT NULL REFERENCES merchant (id),
    square_variation_id VARCHAR(64),
    name                VARCHAR(255)  NOT NULL,
    sku                 VARCHAR(64),
    gtin                VARCHAR(14),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_item_square_variation UNIQUE (merchant_id, square_variation_id),
    CONSTRAINT ck_item_gtin_digits CHECK (gtin ~ '^[0-9]{8}$|^[0-9]{12,14}$')
);

CREATE INDEX ix_item_merchant_name ON item (merchant_id, name);
CREATE INDEX ix_item_merchant_gtin ON item (merchant_id, gtin);
