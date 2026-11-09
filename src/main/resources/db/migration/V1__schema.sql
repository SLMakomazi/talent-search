CREATE TABLE candidate_index (
 id BIGSERIAL PRIMARY KEY,
 source_system TEXT NOT NULL,
 source_candidate_id TEXT NOT NULL,
 profile JSONB NOT NULL,
 cv_hash TEXT NOT NULL,
 source_updated_at TIMESTAMPTZ NOT NULL,
 indexed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE(source_system,source_candidate_id)
);
CREATE INDEX candidate_index_profile_gin ON candidate_index USING GIN(profile);
CREATE TABLE sync_checkpoint (
 source_system TEXT PRIMARY KEY,
 last_updated_at TIMESTAMPTZ NOT NULL,
 last_source_id BIGINT NOT NULL DEFAULT 0,
 last_run_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE vacancies (
 id BIGSERIAL PRIMARY KEY,
 title TEXT NOT NULL,
 required_skills JSONB NOT NULL,
 optional_skills JSONB NOT NULL,
 min_years INTEGER NOT NULL DEFAULT 0,
 location TEXT,
 status TEXT NOT NULL DEFAULT 'POSTED',
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE ranking_results (
 id BIGSERIAL PRIMARY KEY,
 vacancy_id BIGINT NOT NULL REFERENCES vacancies(id),
 candidate_index_id BIGINT NOT NULL REFERENCES candidate_index(id),
 score NUMERIC(5,2) NOT NULL,
 breakdown JSONB NOT NULL,
 explanation TEXT NOT NULL,
 calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ranking_vacancy_idx ON ranking_results(vacancy_id, score DESC);
