ALTER TABLE export_workflows
ADD COLUMN fbo_code VARCHAR(50),
ADD COLUMN variety VARCHAR(100),
ADD COLUMN class_category VARCHAR(100),
ADD COLUMN package_count INTEGER,
ADD COLUMN package_type VARCHAR(50),
ADD COLUMN pallet_count INTEGER,
ADD COLUMN container_number VARCHAR(50),
ADD COLUMN transport_mode VARCHAR(20),
ADD COLUMN inspection_date DATE,
ADD COLUMN certificate_valid_until DATE;
