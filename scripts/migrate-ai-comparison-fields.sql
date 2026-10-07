-- Optional catalogue metadata. Apply on deployments that disable Hibernate schema update.
ALTER TABLE supplier_products ADD COLUMN IF NOT EXISTS comparison_key varchar(150);
ALTER TABLE supplier_products ADD COLUMN IF NOT EXISTS pack_size numeric(12,3);
ALTER TABLE supplier_products ADD COLUMN IF NOT EXISTS pack_unit varchar(10);
