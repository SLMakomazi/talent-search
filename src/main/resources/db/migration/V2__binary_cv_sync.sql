ALTER TABLE sync_checkpoint ALTER COLUMN last_source_id DROP DEFAULT;
ALTER TABLE sync_checkpoint ALTER COLUMN last_source_id TYPE TEXT USING last_source_id::text;
ALTER TABLE sync_checkpoint ALTER COLUMN last_source_id SET DEFAULT '';
-- Reindex legacy plain-text profiles from the new source, preserving vacancies and rankings.
DELETE FROM sync_checkpoint WHERE source_system='company_mock';
UPDATE candidate_index SET active=false WHERE source_system='company_mock';
ALTER TABLE candidate_index ADD COLUMN extraction_status TEXT NOT NULL DEFAULT 'UNKNOWN';
CREATE TABLE candidate_sync_failure (
 source_system TEXT NOT NULL,
 source_candidate_id TEXT NOT NULL,
 attempted_cv_hash TEXT,
 source_updated_at TIMESTAMPTZ NOT NULL,
 extraction_status TEXT NOT NULL DEFAULT 'FAILED',
 error_code TEXT NOT NULL,
 attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(source_system,source_candidate_id)
);
