ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('CUSTOMER', 'DESIGNER', 'ADMIN'));

ALTER TABLE bookings ALTER COLUMN designer_id DROP NOT NULL;

ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
    CHECK (status IN ('PENDING_ASSIGNMENT', 'PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED'));

-- Bootstrap admin account (password: "Admin@123", bcrypt-hashed).
-- Self-registration as ADMIN is blocked in AuthService, so this is currently the
-- only way an admin account comes to exist.
INSERT INTO users (email, password_hash, full_name, phone, role) VALUES
    ('admin@velora.com',
     '$2b$10$2.m5ye7CC7pEa1.qFLsr7upegG3a2Lf.sBdqTZGHRIRmJ0UyCSVs2',
     'Velora Admin',
     NULL,
     'ADMIN')
ON CONFLICT (email) DO NOTHING;
