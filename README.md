# Talent Search — Java 21 / Spring Boot demo

An existing domain-driven modular monolith with two separate PostgreSQL databases in one local container. No external AI, object storage, or messaging infrastructure is used.

```text
Scheduler (startup + every 15 minutes) / POST /api/candidates/sync
 -> company_mock.candidates (SELECT-only JDBC; BYTEA PDF)
 -> SHA-256 comparison -> separate PDFBox CV processor
 -> normalized, structured profile -> talent.candidate_index.profile (JSONB upsert)
 -> vacancy ranking reads current indexed skills, experience and role
```

## Start and synchronize

Requires Docker Engine with Compose. From this folder:

```bash
docker compose up --build -d
docker compose logs -f app
```

Fresh volumes automatically create both databases, a `company_reader` account with SELECT access only, and five fictional candidates with valid binary PDFs. Flyway maintains the application schema; V1 is unchanged and V2 upgrades indexing/checkpoints. Python is not required to run the demo. The first scheduled sync is 10 seconds after startup; subsequent runs are every 15 minutes. Set `SYNC_ENABLED=false` for exclusively manual runs.

```bash
curl -X POST http://localhost:8080/api/candidates/sync
curl http://localhost:8080/api/candidates
```

The initial run returns `{"processed":5,"status":"COMPLETED"}` unless the scheduler has already indexed the candidates. An immediate second run reports zero processed. Responses retain the existing endpoint shapes; candidate profiles now include `candidateNumber`, `name`, structured `experience`, `education`, and `certifications` alongside scoring fields.

### Existing Docker volume

PostgreSQL initialization scripts only run on empty volumes. To preserve an existing demo's vacancies and rankings, first start PostgreSQL with the updated mounts, then explicitly install the new source fixture table and read-only role:

```bash
docker compose up -d postgres
docker compose exec postgres psql -v ON_ERROR_STOP=1 -U demo -d company_mock -f /demo/upgrade-company.sql
docker compose up --build -d app
```

This adds `candidates` without dropping the legacy `company_candidates` table. Its old text records are no longer read. Seed installation uses `ON CONFLICT(candidate_number) DO NOTHING`, preserving later CV replacements. V2 deactivates legacy application profiles and resets the checkpoint so CAN-001 through CAN-005 are indexed from PDFs. Legacy rows remain inactive to preserve historical ranking references.

## Inspect the separate databases

```bash
docker compose exec postgres psql -U demo -d company_mock -c '\d candidates'
docker compose exec postgres psql -U demo -d company_mock -c "SELECT candidate_number,name,surname,email,cv_filename,cv_content_type,octet_length(cv_document) AS cv_bytes,updated_at FROM candidates ORDER BY candidate_number;"
docker compose exec postgres psql -U demo -d talent -c "SELECT source_candidate_id,cv_hash,extraction_status,source_updated_at,indexed_at,jsonb_pretty(profile) FROM candidate_index WHERE active ORDER BY source_candidate_id;"
docker compose exec postgres psql -U demo -d talent -c 'TABLE sync_checkpoint;'
docker compose exec postgres psql -U demo -d talent -c 'TABLE candidate_sync_failure;'
```

The company table contains only the requested identity fields, original CV bytes/metadata and update timestamp. `candidate_number VARCHAR(50)` is its primary key and email is unique. Missing bytes/metadata are allowed so connector failure handling can be demonstrated. The application stores no original CV bytes or extracted full document text.

## Vacancy creation and ranking

```bash
curl -X POST http://localhost:8080/api/vacancies \
 -H 'Content-Type: application/json' \
 -d '{"title":"Java Backend Developer","requiredSkills":["Java","Spring Boot","PostgreSQL"],"optionalSkills":["Docker","AWS"],"minYears":3,"location":"Johannesburg"}'
# Use the returned vacancy ID in these URLs (1 on a fresh database).
curl -X POST http://localhost:8080/api/vacancies/1/rankings
curl http://localhost:8080/api/vacancies/1
```

Weights are preserved: required skills 40, experience 25, role 15, optional skills 10. Location/availability remains unknown and receives zero of its 10 points. Unknown experience is JSON `null`, receives zero experience points and is explained as UNKNOWN. The original project has fixed scoring weights, not a scoring configuration table or controller; this change preserves that existing behavior. Candidate listing/search and ranking use indexed JSONB profiles. Rankings are snapshots: POST rankings again after synchronization to refresh them.

## Demonstrate CAN-001 CV replacement

John's original PDF has Java/Spring Boot/Docker skills and a Java Developer role covering January 2022 through December 2025. The replacement deliberately changes the CV to Python/SQL/Power BI and a Data Engineer role covering January 2020 through December 2025. It removes the old Java/Spring Boot/Docker evidence, making replacement visible in ranking.

```bash
# Observe original profile/hash and rank using the commands above.
docker compose exec postgres psql -v ON_ERROR_STOP=1 -U demo -d company_mock -f /demo/replace-can-001.sql
curl -X POST http://localhost:8080/api/candidates/sync
docker compose exec postgres psql -U demo -d talent -c "SELECT cv_hash,indexed_at,extraction_status,jsonb_pretty(profile) FROM candidate_index WHERE source_system='company_mock' AND source_candidate_id='CAN-001';"
curl -X POST http://localhost:8080/api/vacancies/1/rankings
```

The replacement script updates PDF bytes, filename, MIME type and `updated_at` in the company database using the demo administrator. Normal application synchronization only SELECTs from that database. The new hash differs; extraction produces a complete new profile and the upsert replaces the old JSON rather than merging old skills or jobs. The candidate index ID stays the same. Repeating sync does not duplicate candidates.

To replace the CV with your own local PDF (the database server reads its copied file):

```bash
docker compose cp ./my-cv.pdf postgres:/tmp/replacement.pdf
docker compose exec postgres psql -U demo -d company_mock -c "UPDATE candidates SET cv_document=pg_read_binary_file('/tmp/replacement.pdf'),cv_filename='my-cv.pdf',cv_content_type='application/pdf',updated_at=clock_timestamp() WHERE candidate_number='CAN-001';"
curl -X POST http://localhost:8080/api/candidates/sync
```

## Checkpoints, hashes and failures

* Initial synchronization starts before all fixture timestamps. Incremental synchronization uses a stable `(updated_at, candidate_number COLLATE "C")` cursor, including rows sharing a timestamp, in batches of 100.
* A transaction-scoped PostgreSQL advisory lock coordinates scheduled/manual runs across application instances. Profiles, failure resolution and the checkpoint commit together in the talent database. A database error rolls back the run. The source connection is separate and read-only.
* SHA-256 is computed from original bytes. Unchanged documents reuse their extracted fields while identity changes still refresh the JSON. Changed bytes trigger PDFBox extraction. Unique `(source_system, source_candidate_id)` ensures upserts preserve candidate IDs.
* Missing, oversized (>10 MiB), encrypted, invalid, textless/scanned or unsupported files are recorded in `candidate_sync_failure` with attempted hash, source timestamp, attempt time and status. DOCX metadata can be stored by the source, but this PDF-only processor records it as unsupported.
* Failed extraction leaves the current index/hash/status untouched. A PARTIAL replacement also preserves an existing PARSED profile. Failure status belongs to the separate attempt record, avoiding ambiguity with the last successful profile.
* A failure returns `FAILED_AT_CAN-xxx` and stops before advancing that record's checkpoint. Earlier successful candidates commit; later candidates wait. Correct the source CV and update its timestamp, then retry. This deliberate head-of-line behavior avoids silently losing failures. A first readable but incomplete CV can be indexed as PARTIAL with unknown fields.
* `indexed_at` is the last successful synchronization time; `source_updated_at` is the accepted source timestamp; `cv_hash` is the accepted document hash; `extraction_status` is PARSED/PARTIAL. No failed attempt moves the checkpoint beyond itself.

The source must update `updated_at` for every identity/CV change and use fresh, forward-moving timestamps. As with timestamp polling generally, backdated writes or long source transactions committing out of timestamp order can be missed. This local demo does not implement CDC, delete reconciliation or replay windows. For a full replay after editing skill rules, delete the company_mock checkpoint in `talent` and set its indexed `cv_hash` to an empty string before syncing; otherwise unchanged hashes reuse cached extraction.

## Extraction rules and fixtures

`CvParser` handles PDF bytes separately from JDBC. PDFBox extracts text; line endings, repeated horizontal whitespace and nonbreaking spaces are normalized. Skills are matched case-insensitively with token boundaries from `app.cv.skills` (`CV_SKILLS`, a comma-separated override). Skill recognition uses the entire CV, including certifications; it is deterministic dictionary matching, not semantic inference.

Reliable structured lines use:

```text
Experience: Java Developer | Acacia Demo Systems | 2022-01 | 2025-12
Education: Diploma in Information Technology
Certification: AWS Cloud Practitioner
```

Multiple experience lines are supported; put the most relevant/current role first. End dates can be `PRESENT`. Experience years are completed years from the union of inclusive employment months, so overlapping roles are not double-counted. Invalid/reversed/future dates make experience unknown. Absent education/certifications and role are explicitly UNKNOWN; an empty experience/skills array means no reliable matches. Arbitrary layouts and scanned PDFs need further processing; no values are invented and no external AI is called.

Fixture files are under `output/pdf/`: John (Java), Mary (DevOps), Anele (frontend), Priya (data analysis), Thabo (Azure/cloud) and John's replacement. All professional information is fictional. `scripts/generate_demo_cvs.py` generates the six PDFs plus committed SQL containing their bytes. To regenerate fixtures during development only:

```bash
python3 -m pip install reportlab
python3 scripts/generate_demo_cvs.py
```

Generated SQL is already included in the working folder and mounted read-only at `/demo`; no generator runs inside the application or at synchronization time.

## Changed implementation

* Updated: `build.gradle` (PDFBox), `SourceCandidate`, `CandidateProfile`, `CompanyCandidateConnector`, `CvParser`, `SyncService`, `CandidateIndexRepository`, `ScoringService`, `SourceDataSourceConfig`, `application.yml`, `docker-compose.yml`, `docker/postgres/init/01-company.sql`, and this README.
* Added: `db/migration/V2__binary_cv_sync.sql`, `docker/postgres/company-candidates.sql`, `docker/postgres/replace-can-001.sql`, `docker/postgres/upgrade-company.sql`, `scripts/generate_demo_cvs.py`, and six PDFs under `output/pdf/`.
* Preserved: V1 migration, scheduled sync, REST controllers, vacancy and ranking persistence, existing scoring behavior and test sources. The explicit primary application JDBC template prevents the additional source JDBC bean from displacing the application's own template.

Java 21 compilation and Compose configuration validation passed; all six PDF fixtures were rendered and visually reviewed. No tests have been run for this change, as requested. Docker Engine was not running on the implementation machine, so container startup and the end-to-end database workflow have not been executed. No Gradle wrapper is supplied; the Docker builder uses Gradle 8.14 / Java 21. This remains an unauthenticated local demo bound to loopback ports.

To delete all local demo databases deliberately: `docker compose down -v`.
