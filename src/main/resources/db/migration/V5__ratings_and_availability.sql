ALTER TABLE designer_profiles
    ADD COLUMN availability_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (availability_status IN ('AVAILABLE', 'UNAVAILABLE')),
    ADD COLUMN average_rating NUMERIC(3, 2),
    ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE bookings
    ADD COLUMN category_id BIGINT REFERENCES categories (id),
    ADD COLUMN preferred_style VARCHAR(100),
    ADD COLUMN budget NUMERIC(12, 2),
    ADD COLUMN location VARCHAR(150);

CREATE TABLE reviews (
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL UNIQUE REFERENCES bookings (id),
    customer_id     BIGINT NOT NULL REFERENCES users (id),
    designer_id     BIGINT NOT NULL REFERENCES users (id),
    rating          SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         VARCHAR(1000),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_reviews_designer_id ON reviews (designer_id);
