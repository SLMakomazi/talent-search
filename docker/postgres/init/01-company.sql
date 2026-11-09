CREATE DATABASE company_mock;
\connect company_mock
\i /demo/company-candidates.sql
CREATE ROLE company_reader LOGIN PASSWORD 'company_reader_demo';
GRANT CONNECT ON DATABASE company_mock TO company_reader;
GRANT USAGE ON SCHEMA public TO company_reader;
GRANT SELECT ON candidates TO company_reader;
ALTER ROLE company_reader SET default_transaction_read_only = on;
