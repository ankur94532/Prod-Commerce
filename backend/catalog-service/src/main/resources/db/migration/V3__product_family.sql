-- Groups the variants of one product so a search result page cannot fill with six
-- near-identical rows. Search collapses on this value; without it, "laptop backpack"
-- spends every slot on colours of the same bag.
--
-- The catalog owns the grouping because only the catalog knows it. Deriving it in the
-- search indexer from the slug is a heuristic that mis-groups names which happen to end
-- in a word and a number ("sony-wh-1000" is one product, not variant 1000 of "sony-wh"),
-- and search keeps that derivation only as a fallback for rows written before this column.
ALTER TABLE products ADD COLUMN IF NOT EXISTS product_family VARCHAR(255);

-- Backfill. Existing variant slugs were generated as "<base slug>-<descriptor>-<n>", so
-- that shape is stripped; everything else is its own family of one. Rows the pattern does
-- not match are unaffected rather than guessed at.
UPDATE products
   SET product_family = COALESCE(substring(slug from '^(.*)-[a-z]{3,}-[0-9]{1,3}$'), slug)
 WHERE product_family IS NULL;

-- Enforced from here on: the entity defaults a blank family to the slug before persisting,
-- so a product with no family is a bug, not a state the table should be able to hold.
ALTER TABLE products ALTER COLUMN product_family SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_products_product_family ON products (product_family);
