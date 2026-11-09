CREATE TABLE scoring_configuration (
 name VARCHAR(100) PRIMARY KEY,
 weights JSONB NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO scoring_configuration(name,weights) VALUES ('default',
 '{"requiredSkillsWeight":40,"experienceWeight":25,"roleWeight":15,"optionalSkillsWeight":10,"locationAvailabilityWeight":10}');
ALTER TABLE ranking_results ADD COLUMN candidate_snapshot JSONB;
ALTER TABLE ranking_results ADD COLUMN scoring_configuration_name VARCHAR(100);
ALTER TABLE ranking_results ADD COLUMN scoring_weights JSONB;
