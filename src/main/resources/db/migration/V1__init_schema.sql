CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(20),
    role            VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'DESIGNER')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE designer_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    bio                 TEXT,
    years_experience    INTEGER,
    specialization      VARCHAR(150),
    city                VARCHAR(100),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(500)
);

CREATE TABLE designs (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    category_id         BIGINT NOT NULL REFERENCES categories (id),
    designer_id         BIGINT NOT NULL REFERENCES users (id),
    cover_image_url     VARCHAR(1000),
    price_estimate      NUMERIC(12, 2),
    style_tag           VARCHAR(100),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_designs_category_id ON designs (category_id);
CREATE INDEX idx_designs_designer_id ON designs (designer_id);
CREATE INDEX idx_designs_style_tag ON designs (style_tag);

CREATE TABLE bookings (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES users (id),
    designer_id     BIGINT NOT NULL REFERENCES users (id),
    design_id       BIGINT REFERENCES designs (id),
    scheduled_at    TIMESTAMP NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    notes           VARCHAR(1000),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_bookings_customer_id ON bookings (customer_id);
CREATE INDEX idx_bookings_designer_id ON bookings (designer_id);
CREATE INDEX idx_bookings_status ON bookings (status);
