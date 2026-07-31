CREATE TABLE quotations (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL UNIQUE REFERENCES bookings (id),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED')),
    total_amount    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    notes           VARCHAR(2000),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE quotation_line_items (
    id              BIGSERIAL PRIMARY KEY,
    quotation_id    BIGINT NOT NULL REFERENCES quotations (id) ON DELETE CASCADE,
    description     VARCHAR(300) NOT NULL,
    quantity        NUMERIC(10, 2),
    unit            VARCHAR(30),
    unit_price      NUMERIC(12, 2),
    amount          NUMERIC(12, 2) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_quotation_line_items_quotation_id ON quotation_line_items (quotation_id);
