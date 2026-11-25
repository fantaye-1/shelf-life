-- Batches (one delivery of one item at one location, with one expiry date) and waste records.
-- merchant_id is repeated on each table so every query can be scoped by tenant, and the
-- composite foreign keys below make it impossible to attach a batch to another store's item.

ALTER TABLE item     ADD CONSTRAINT uq_item_merchant_id     UNIQUE (merchant_id, id);
ALTER TABLE location ADD CONSTRAINT uq_location_merchant_id UNIQUE (merchant_id, id);

CREATE TABLE batch (
    id                 UUID         PRIMARY KEY,
    merchant_id        UUID         NOT NULL REFERENCES merchant (id),
    item_id            UUID         NOT NULL,
    location_id        UUID         NOT NULL,
    -- A calendar date in the location's own time zone.
    expires_on         DATE         NOT NULL,
    quantity_received  INTEGER      NOT NULL,
    quantity_remaining INTEGER      NOT NULL,
    -- Money is integer cents. NULL means the cost was not entered.
    unit_cost_cents    INTEGER,
    status             VARCHAR(16)  NOT NULL,
    version            BIGINT       NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_batch_item     FOREIGN KEY (merchant_id, item_id)     REFERENCES item (merchant_id, id),
    CONSTRAINT fk_batch_location FOREIGN KEY (merchant_id, location_id) REFERENCES location (merchant_id, id),
    CONSTRAINT uq_batch_merchant_id UNIQUE (merchant_id, id),
    CONSTRAINT ck_batch_quantity_received  CHECK (quantity_received > 0),
    CONSTRAINT ck_batch_quantity_remaining CHECK (quantity_remaining BETWEEN 0 AND quantity_received),
    CONSTRAINT ck_batch_unit_cost          CHECK (unit_cost_cents IS NULL OR unit_cost_cents >= 0),
    CONSTRAINT ck_batch_status             CHECK (status IN ('ACTIVE', 'MARKED_DOWN', 'SOLD_OUT', 'WASTED')),
    -- Closed batches have nothing left on the shelf; open ones still do.
    CONSTRAINT ck_batch_status_remaining   CHECK ((status IN ('SOLD_OUT', 'WASTED')) = (quantity_remaining = 0))
);

-- "What expires soon at this store" (M3) reads open batches by location and date.
CREATE INDEX ix_batch_location_expiry ON batch (merchant_id, location_id, expires_on);
CREATE INDEX ix_batch_item ON batch (merchant_id, item_id);

CREATE TABLE waste_entry (
    id          UUID         PRIMARY KEY,
    merchant_id UUID         NOT NULL REFERENCES merchant (id),
    batch_id    UUID         NOT NULL,
    quantity    INTEGER      NOT NULL,
    -- quantity x unit cost, in cents. NULL when the batch has no unit cost.
    loss_cents  BIGINT,
    recorded_at TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_waste_batch FOREIGN KEY (merchant_id, batch_id) REFERENCES batch (merchant_id, id),
    CONSTRAINT ck_waste_quantity CHECK (quantity > 0),
    CONSTRAINT ck_waste_loss     CHECK (loss_cents IS NULL OR loss_cents >= 0)
);

-- The monthly waste report (M3/M4) reads by merchant and time.
CREATE INDEX ix_waste_merchant_recorded ON waste_entry (merchant_id, recorded_at);
CREATE INDEX ix_waste_batch ON waste_entry (merchant_id, batch_id);
