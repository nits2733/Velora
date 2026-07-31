-- Rename the professional-identity concept: a plumber/electrician/carpenter is not
-- a "designer" just because they share the same account type as one. Individual
-- Services (painting, plumbing, electrical, carpentry, etc.) are handled by the same
-- kind of account as an interior designer, so the role/entity naming needs to reflect
-- that broader meaning.
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('CUSTOMER', 'DESIGNER', 'PROFESSIONAL', 'ADMIN'));
UPDATE users SET role = 'PROFESSIONAL' WHERE role = 'DESIGNER';
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('CUSTOMER', 'PROFESSIONAL', 'ADMIN'));

ALTER TABLE designer_profiles RENAME TO professional_profiles;

ALTER TABLE bookings RENAME COLUMN designer_id TO professional_id;
ALTER TABLE designs   RENAME COLUMN designer_id TO professional_id;
ALTER TABLE reviews   RENAME COLUMN designer_id TO professional_id;

-- Full Home Services (Velora manages the whole project, interior design + every
-- trade needed) vs a standalone Individual Service request (one trade, no
-- portfolio-browsing step, always assigned by Velora).
-- DEFAULT + DROP DEFAULT: backfills existing rows (all created before this journey
-- split existed, so all genuinely were full-project bookings) without leaving an
-- implicit default for the application to rely on going forward.
ALTER TABLE bookings ADD COLUMN request_type VARCHAR(30) NOT NULL DEFAULT 'FULL_HOME_PROJECT'
    CHECK (request_type IN ('FULL_HOME_PROJECT', 'INDIVIDUAL_SERVICE'));
ALTER TABLE bookings ALTER COLUMN request_type DROP DEFAULT;

-- Interior-project room categories vs standalone trade-service categories.
ALTER TABLE categories ADD COLUMN service_group VARCHAR(30) NOT NULL DEFAULT 'HOME_PROJECT'
    CHECK (service_group IN ('HOME_PROJECT', 'INDIVIDUAL_SERVICE'));
ALTER TABLE categories ALTER COLUMN service_group DROP DEFAULT;

INSERT INTO categories (name, description, service_group) VALUES
    ('Painting', 'Interior and exterior painting services', 'INDIVIDUAL_SERVICE'),
    ('Plumbing', 'Plumbing repair and installation', 'INDIVIDUAL_SERVICE'),
    ('Electrical', 'Electrical repair and installation', 'INDIVIDUAL_SERVICE'),
    ('Carpentry', 'Custom carpentry and woodwork', 'INDIVIDUAL_SERVICE'),
    ('False Ceiling', 'False ceiling design and installation', 'INDIVIDUAL_SERVICE'),
    ('Modular Kitchen', 'Modular kitchen installation', 'INDIVIDUAL_SERVICE')
ON CONFLICT (name) DO NOTHING;
