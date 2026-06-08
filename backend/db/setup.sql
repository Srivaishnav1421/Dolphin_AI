-- ═══════════════════════════════════════════════════════════════════
--  DolphinAI — PostgreSQL Database Setup
--  Run once as postgres superuser: psql -U postgres -f setup.sql
-- ═══════════════════════════════════════════════════════════════════

-- Create DB and user
CREATE USER dolphin WITH PASSWORD 'dolphin123';
CREATE DATABASE dolphindb OWNER dolphin;
GRANT ALL PRIVILEGES ON DATABASE dolphindb TO dolphin;

-- Connect to dolphindb
\c dolphindb;
GRANT ALL ON SCHEMA public TO dolphin;

-- ── Tables are auto-created by Spring JPA (ddl-auto=update) ─────────
-- This file handles DB/user creation and RLS policies.

-- ── Row-Level Security Setup ──────────────────────────────────────────
-- Called after Spring Boot has created all tables via Hibernate.
-- Run this script AFTER the first successful application boot.

-- Create a config parameter for tenant isolation
-- Application sets: SET LOCAL app.workspace_id = '<accountId>';

DO $$
DECLARE
    tbl RECORD;
    col_name TEXT;
BEGIN
    -- Query all user tables in public schema
    FOR tbl IN 
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_type = 'BASE TABLE'
    LOOP
        col_name := NULL;
        
        -- Check if table has account_id column
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = 'public' 
              AND table_name = tbl.table_name 
              AND column_name = 'account_id'
        ) THEN
            col_name := 'account_id';
        -- Check if table has workspace_id column
        ELSIF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = 'public' 
              AND table_name = tbl.table_name 
              AND column_name = 'workspace_id'
        ) THEN
            col_name := 'workspace_id';
        END IF;

        -- Apply RLS if tenant column exists
        IF col_name IS NOT NULL THEN
            BEGIN
                EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl.table_name);
                EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl.table_name);
                EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl.table_name);
                EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (%I = current_setting(''app.workspace_id'', true))', tbl.table_name, col_name);
                RAISE NOTICE '🛡️ Row-Level Security enabled on table % using % (fail-closed)', tbl.table_name, col_name;
            EXCEPTION WHEN OTHERS THEN
                RAISE WARNING 'Could not enable RLS on table %: %', tbl.table_name, SQLERRM;
            END;
        END IF;
    END LOOP;
END;
$$;

-- ── Organization + Workspace tables (no RLS — global admin scope) ───
-- organizations and workspaces are multi-tenant root tables,
-- access controlled at the application layer (RBAC).

-- ── Grant the dolphin user permission to use SET LOCAL ───────────────
ALTER ROLE dolphin SET session_replication_role = DEFAULT;

\echo '✅ dolphindb database, dolphin user, and RLS policies configured!'
\echo 'ℹ️  Run this file AFTER the first Spring Boot boot to activate RLS on Hibernate-generated tables.'
