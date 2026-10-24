-- Flyway callback: runs before every migrate, so it must be safe to repeat.
--
-- One-time adoption of a database created by an earlier main (Oct 2026). That release created
-- vault_documents, consent_records and export_requirements through Hibernate ddl-auto=update with a
-- different layout. Before V2 is applied, such tables are renamed to legacy_team_<name> (data kept)
-- so V2/V5 can create the current tables.
--
-- Once V2 has been applied this callback does nothing: the tables then belong to this schema, even if
-- an older release later added extra columns to them (V7 makes such leftovers harmless).
DO $$
DECLARE
    t        record;
    obj      record;
    new_name text;
    target   text;
BEGIN
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '2' AND success) THEN
            RETURN;
        END IF;
    END IF;

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

        -- Constraints first (renaming a primary-key/unique constraint also renames its index).
        FOR obj IN SELECT conname FROM pg_constraint WHERE conrelid = quote_ident(t.tbl)::regclass
        LOOP
            target := left('legacy_team_' || obj.conname, 63);
            IF to_regclass(quote_ident(target)) IS NOT NULL THEN
                target := left('legacy_team_' || to_char(clock_timestamp(), 'HH24MISSUS') || '_' || obj.conname, 63);
            END IF;
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', t.tbl, obj.conname, target);
        END LOOP;

        -- Remaining indexes not tied to a constraint.
        FOR obj IN SELECT i.relname
                   FROM pg_index x JOIN pg_class i ON i.oid = x.indexrelid
                   WHERE x.indrelid = quote_ident(t.tbl)::regclass AND i.relname NOT LIKE 'legacy_team_%'
        LOOP
            target := left('legacy_team_' || obj.relname, 63);
            IF to_regclass(quote_ident(target)) IS NOT NULL THEN
                target := left('legacy_team_' || to_char(clock_timestamp(), 'HH24MISSUS') || '_' || obj.relname, 63);
            END IF;
            EXECUTE format('ALTER INDEX %I RENAME TO %I', obj.relname, target);
        END LOOP;

        -- Identity / serial sequences owned by the table.
        FOR obj IN SELECT s.relname
                   FROM pg_depend d JOIN pg_class s ON s.oid = d.objid AND s.relkind = 'S'
                   WHERE d.refobjid = quote_ident(t.tbl)::regclass
        LOOP
            target := left('legacy_team_' || obj.relname, 63);
            IF to_regclass(quote_ident(target)) IS NOT NULL THEN
                target := left('legacy_team_' || to_char(clock_timestamp(), 'HH24MISSUS') || '_' || obj.relname, 63);
            END IF;
            EXECUTE format('ALTER SEQUENCE %I RENAME TO %I', obj.relname, target);
        END LOOP;

        EXECUTE format('ALTER TABLE %I RENAME TO %I', t.tbl, new_name);
        RAISE NOTICE 'Set aside earlier % table as %', t.tbl, new_name;
    END LOOP;
END
$$;
