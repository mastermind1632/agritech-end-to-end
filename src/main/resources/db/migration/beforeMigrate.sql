-- Flyway callback: runs before every migrate, so it must be safe to repeat.
-- An earlier main created vault_documents, consent_records and export_requirements with a different
-- layout. If found, they are renamed to legacy_team_<name> (data kept) so V2/V5 can create the
-- current tables. On fresh or current databases this does nothing.
DO $$
DECLARE
    t        record;
    obj      record;
    new_name text;
BEGIN
    FOR t IN SELECT * FROM (VALUES
                ('vault_documents',     'storage_path'),
                ('consent_records',     'consent_type'),
                ('export_requirements', 'catalog_key')) AS v(tbl, marker)
    LOOP
        CONTINUE WHEN NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = t.tbl AND column_name = t.marker);

        new_name := 'legacy_team_' || t.tbl;
        IF to_regclass(quote_ident(new_name)) IS NOT NULL THEN
            new_name := left(new_name || '_' || to_char(clock_timestamp(), 'YYYYMMDDHH24MISS'), 63);
        END IF;

        FOR obj IN SELECT conname FROM pg_constraint WHERE conrelid = quote_ident(t.tbl)::regclass
        LOOP
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                           t.tbl, obj.conname, left('legacy_team_' || obj.conname, 63));
        END LOOP;

        FOR obj IN SELECT i.relname
                   FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid
                   WHERE x.indrelid = quote_ident(t.tbl)::regclass AND i.relname NOT LIKE 'legacy_team_%'
        LOOP
            EXECUTE format('ALTER INDEX %I RENAME TO %I', obj.relname, left('legacy_team_' || obj.relname, 63));
        END LOOP;

        FOR obj IN SELECT s.relname
                   FROM pg_depend d JOIN pg_class s ON s.oid = d.objid AND s.relkind = 'S'
                   WHERE d.refobjid = quote_ident(t.tbl)::regclass
        LOOP
            EXECUTE format('ALTER SEQUENCE %I RENAME TO %I', obj.relname, left('legacy_team_' || obj.relname, 63));
        END LOOP;

        EXECUTE format('ALTER TABLE %I RENAME TO %I', t.tbl, new_name);
        RAISE NOTICE 'Set aside earlier % table as %', t.tbl, new_name;
    END LOOP;
END
$$;