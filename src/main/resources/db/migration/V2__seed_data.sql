-- Seed data for MVP 1: categories, a demo designer, their profile, and sample designs.
-- Written to be safe to run against a database that may already have some of this data
-- (uses ON CONFLICT / NOT EXISTS guards throughout instead of assuming an empty table).

-- 1. Categories
INSERT INTO categories (name, description) VALUES
    ('Living Room', 'Designs for lounges, family rooms, and shared living spaces'),
    ('Bedroom', 'Designs for master, guest, and kids'' bedrooms'),
    ('Kitchen', 'Modular and open-plan kitchen designs'),
    ('Bathroom', 'Bathroom and powder room designs'),
    ('Office/Study', 'Home office and study room designs'),
    ('Dining Room', 'Dining and entertaining space designs'),
    ('Balcony/Outdoor', 'Balcony, patio, and outdoor deck designs')
ON CONFLICT (name) DO NOTHING;

-- 2. Demo designer account (password: "Designer@123", bcrypt-hashed)
INSERT INTO users (email, password_hash, full_name, phone, role) VALUES
    ('designer@velora.com',
     '$2b$10$ZHCniA3kmcDiopSwwzlWyOgU/eIsRyP/FrMTZ.QkBCeplOAMxz3yK',
     'Velora Studio',
     '+91-9000000001',
     'DESIGNER')
ON CONFLICT (email) DO NOTHING;

-- 3. Designer profile for the demo designer
INSERT INTO designer_profiles (user_id, bio, years_experience, specialization, city)
SELECT u.id,
       'Award-winning interior design studio specializing in modern Indian aesthetics',
       8,
       'Modern Minimalist',
       'Mumbai'
FROM users u
WHERE u.email = 'designer@velora.com'
ON CONFLICT (user_id) DO NOTHING;

-- 4. Sample designs (2 per category), all published by the demo designer
INSERT INTO designs (title, description, category_id, designer_id, cover_image_url, price_estimate, style_tag)
SELECT v.title, v.description, c.id, u.id, v.cover_image_url, v.price_estimate, v.style_tag
FROM (VALUES
    -- Living Room
    ('Serene Minimalist Living Room',
     'An open, clutter-free living room built around neutral tones, low-profile furniture, and natural light.',
     'Living Room', 'https://images.unsplash.com/photo-1600210492486-724fe5c67fb0', 85000.00, 'minimalist'),
    ('Contemporary Family Lounge',
     'A warm, contemporary lounge with layered textures, a statement rug, and flexible seating for families.',
     'Living Room', 'https://images.unsplash.com/photo-1584622650111-993a426fbf0a', 120000.00, 'contemporary'),

    -- Bedroom
    ('Scandinavian Master Bedroom',
     'A calm, light-filled master bedroom using Scandinavian principles: light wood, soft linens, minimal ornamentation.',
     'Bedroom', 'https://images.unsplash.com/photo-1616594039964-ae9021efabc3', 65000.00, 'scandinavian'),
    ('Traditional Teak Bedroom Suite',
     'A traditional bedroom design featuring solid teak furniture, warm textiles, and classic Indian motifs.',
     'Bedroom', 'https://images.unsplash.com/photo-1615873968403-89e068629265', 150000.00, 'traditional'),

    -- Kitchen
    ('Modern Modular Kitchen',
     'A sleek modular kitchen with handleless cabinetry, quartz countertops, and integrated appliances.',
     'Kitchen', 'https://images.unsplash.com/photo-1556909212-d5b604d0c90d', 140000.00, 'modern'),
    ('Industrial Open-Plan Kitchen',
     'An open-plan kitchen with exposed brick, matte-black fixtures, and factory-style pendant lighting.',
     'Kitchen', 'https://images.unsplash.com/photo-1556911220-e15b29be8c8f', 130000.00, 'industrial'),

    -- Bathroom
    ('Minimalist Spa Bathroom',
     'A spa-inspired bathroom with a freestanding tub, matte tiles, and a restrained, minimalist palette.',
     'Bathroom', 'https://images.unsplash.com/photo-1620626011761-996317b8d101', 95000.00, 'minimalist'),
    ('Contemporary Powder Room',
     'A compact powder room refresh with a floating vanity, bold accent tile, and contemporary fixtures.',
     'Bathroom', 'https://images.unsplash.com/photo-1620626011761-996317b8d102', 45000.00, 'contemporary'),

    -- Office/Study
    ('Modern Home Office',
     'A productivity-focused home office with an ergonomic desk setup, cable management, and task lighting.',
     'Office/Study', 'https://images.unsplash.com/photo-1593476123561-9516f2097158', 55000.00, 'modern'),
    ('Traditional Study Room',
     'A classic study room with built-in wooden bookshelves, a writing desk, and warm ambient lighting.',
     'Office/Study', 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c', 70000.00, 'traditional'),

    -- Dining Room
    ('Scandinavian Dining Nook',
     'A cozy Scandinavian dining nook with a light oak table, woven chairs, and a statement pendant light.',
     'Dining Room', 'https://images.unsplash.com/photo-1617806118233-18e1de247200', 60000.00, 'scandinavian'),
    ('Contemporary Formal Dining Room',
     'A formal dining room with a large glass-top table, upholstered seating, and layered ambient lighting.',
     'Dining Room', 'https://images.unsplash.com/photo-1617806118233-18e1de247201', 145000.00, 'contemporary'),

    -- Balcony/Outdoor
    ('Minimalist Balcony Garden',
     'A small-footprint balcony transformed into a green retreat with built-in planters and folding seating.',
     'Balcony/Outdoor', 'https://images.unsplash.com/photo-1600607687939-ce8a6c25118c', 35000.00, 'minimalist'),
    ('Modern Outdoor Deck Lounge',
     'A modern outdoor deck with weatherproof modular seating, string lighting, and an outdoor rug.',
     'Balcony/Outdoor', 'https://images.unsplash.com/photo-1600607687920-4e2a09cf159d', 90000.00, 'modern')
) AS v(title, description, category_name, cover_image_url, price_estimate, style_tag)
JOIN categories c ON c.name = v.category_name
CROSS JOIN (SELECT id FROM users WHERE email = 'designer@velora.com') AS u
WHERE NOT EXISTS (
    SELECT 1 FROM designs d WHERE d.title = v.title AND d.designer_id = u.id
);
