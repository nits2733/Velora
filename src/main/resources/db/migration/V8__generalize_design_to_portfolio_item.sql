ALTER TABLE designs RENAME TO portfolio_items;
ALTER TABLE bookings RENAME COLUMN design_id TO portfolio_item_id;

CREATE TABLE interior_design_details (
    id                  BIGSERIAL PRIMARY KEY,
    portfolio_item_id   BIGINT NOT NULL UNIQUE REFERENCES portfolio_items (id) ON DELETE CASCADE,
    style_tag           VARCHAR(100),
    price_estimate      NUMERIC(12, 2)
);

-- Every portfolio item created so far (all via V2's seed data) was interior-design
-- work - backfill their style/price into the new satellite table before dropping
-- the columns from the base table.
INSERT INTO interior_design_details (portfolio_item_id, style_tag, price_estimate)
SELECT id, style_tag, price_estimate FROM portfolio_items;

ALTER TABLE portfolio_items DROP COLUMN style_tag;
ALTER TABLE portfolio_items DROP COLUMN price_estimate;
-- (dropping style_tag also drops idx_designs_style_tag automatically; recreated below
-- on the new location of that column.)

ALTER INDEX idx_designs_category_id RENAME TO idx_portfolio_items_category_id;
ALTER INDEX idx_designs_designer_id RENAME TO idx_portfolio_items_professional_id;
CREATE INDEX idx_interior_design_details_style_tag ON interior_design_details (style_tag);
