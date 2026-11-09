-- Run explicitly as demo against company_mock for pre-existing Docker volumes.
\i /demo/company-candidates.sql
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='company_reader') THEN
  CREATE ROLE company_reader LOGIN PASSWORD 'company_reader_demo';
 END IF;
END $$;
GRANT CONNECT ON DATABASE company_mock TO company_reader;
GRANT USAGE ON SCHEMA public TO company_reader;
GRANT SELECT ON candidates TO company_reader;
ALTER ROLE company_reader SET default_transaction_read_only = on;
