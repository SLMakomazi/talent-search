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

Fresh volumes automatically create both databases, a `company_reader` account with SELECT access only, and five fictional candidates with valid binary PDFs. Flyway maintains the application schema; V1 is unchanged, V2 upgrades indexing/checkpoints, and V3 adds scoring configurations and ranking snapshots. Python is not required to run the demo. The first scheduled sync is 10 seconds after startup; subsequent runs are every 15 minutes. Set `SYNC_ENABLED=false` for exclusively manual runs.

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

Default weights are preserved: required skills 40, experience 25, role 15, optional skills 10. Location/availability remains unknown and receives zero of its 10 points. Unknown experience is JSON `null`, receives zero experience points and is explained as UNKNOWN. Scoring configurations are now stored in PostgreSQL and can be read or updated through the API; weights must be between zero and 100 and sum to 100. Candidate listing/search and ranking use indexed JSONB profiles. Rankings are snapshots: POST rankings again after synchronization to refresh them.

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

The source must update `updated_at` for every identity/CV change and use fresh, forward-moving timestamps. As with timestamp polling generally, backdated writes or long source transactions committing out of timestamp order can be missed. This local demo does not implement CDC, delete reconciliation or replay windows. For a full replay after editing skill rules, call `POST /api/candidates/sync?full=true`. This resets the cursor and forces document extraction instead of reusing unchanged hashes. The same lock, failure protection and upsert rules apply.

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

Java 21 compilation and executable JAR packaging passed. The PDF fixtures were rendered and visually reviewed during their creation. Automated tests remain unexecuted as requested. Runtime verification is described below. No Gradle wrapper is supplied; the Docker builder uses Gradle 8.14 / Java 21. This remains an unauthenticated local demo bound to loopback ports.

To delete all local demo databases deliberately: `docker compose down -v`.


## Complete API workflow

This project is a REST backend. It does not currently serve a recruiter web interface. Open `/actuator/health` for application health; use these endpoints to operate the backend. Java implementation files are under `src/main/java/za/co/talentsearch`.

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/candidates` | Search indexed candidate profiles |
| GET | `/api/candidates/{id}` | Read one active indexed candidate by its application index ID |
| POST | `/api/candidates/sync` | Run incremental sync |
| POST | `/api/candidates/sync?full=true` | Force full extraction and reindex |
| GET | `/api/candidates/sync/status` | Inspect checkpoint and outstanding document failures |
| POST | `/api/vacancies` | Create a validated vacancy |
| GET | `/api/vacancies` | List vacancies (limit/offset supported) |
| GET | `/api/vacancies/{id}` | Read one vacancy |
| GET | `/api/scoring-configurations` | List named scoring configurations |
| GET | `/api/scoring-configurations/{name}` | Read a scoring configuration |
| PUT | `/api/scoring-configurations/{name}` | Create or update scoring weights |
| POST | `/api/vacancies/{id}/rankings` | Rank with the default configuration and save results |
| POST | `/api/vacancies/{id}/rankings?configuration=skills-first` | Rank with a named configuration |
| GET | `/api/vacancies/{id}/rankings` | Read saved ranking snapshots without recomputing |

Candidate query parameters are `query` (literal case-insensitive identity/role substring), `skill` (case-insensitive exact skill), `minYears`, `limit` (1–500, default 100) and `offset` (default zero). Filters combine with AND. Unknown experience does not pass a minimum-experience filter. Search uses bound PostgreSQL parameters against JSONB data. Ranking considers all active indexed candidates, independent of search pagination.

```bash
curl http://localhost:8080/actuator/health
curl 'http://localhost:8080/api/candidates?skill=Java&minYears=3&limit=20'
curl 'http://localhost:8080/api/candidates?query=engineer'
curl http://localhost:8080/api/candidates/sync/status
curl http://localhost:8080/api/vacancies
curl http://localhost:8080/api/scoring-configurations
curl -X PUT http://localhost:8080/api/scoring-configurations/skills-first \
 -H 'Content-Type: application/json' \
 -d '{"requiredSkillsWeight":60,"experienceWeight":15,"roleWeight":10,"optionalSkillsWeight":15,"locationAvailabilityWeight":0}'
curl -X POST 'http://localhost:8080/api/vacancies/1/rankings?configuration=skills-first'
curl http://localhost:8080/api/vacancies/1/rankings
```

Ranking persists the candidate identity, score breakdown, explanation, configuration name and actual weights used. Future configuration or identity changes do not silently alter a saved snapshot. Refresh with POST to apply current profiles and weights. Concurrent ranking requests for the same vacancy serialize using a PostgreSQL row lock.

Validation returns HTTP 400 with problem details, unknown resources return HTTP 404, and database failures return HTTP 503 with details in server logs. Candidates and vacancies use numeric application IDs in detail URLs; the external company reference remains `profile.candidateNumber`.

Additional implementation files: `candidate/application/CvProcessingException`, `candidate/infrastructure/SyncStateRepository`, `ranking/infrastructure/RankingRepository`, `scoring/domain/ScoreWeights`, `scoring/infrastructure/ScoringConfigurationRepository`, `scoring/api/ScoringConfigurationController`, `shared/api/ApiExceptionHandler`, `V3__scoring_and_ranking_snapshots.sql`, and `.dockerignore`. Existing Java files have been formatted for readability. Database state handling now belongs to infrastructure repositories, with synchronization and ranking orchestration in application services.

### Runtime verification completed

The application was packaged with Java 21 and started with `docker compose up --build -d`. PostgreSQL initialization created the five binary CV records; Flyway applied V1–V3; the scheduler extracted all five profiles with PARSED status. Application health returned UP. Manual API use confirmed candidate skill/experience filtering, default scoring configuration retrieval, named configuration creation, vacancy creation, ranking calculation and saved ranking retrieval. John ranked at 90 with the default weights for the example Java vacancy. Repeating synchronization returned zero processed and no failures. The demo database now contains that example vacancy and the `skills-first` configuration. All automated tests remain unrun.

Gradle plugin resolution checks Maven Central before the plugin portal to avoid depending on the portal's redirected artifact host when the same plugins are available from Maven Central. `.dockerignore` excludes local caches, environment files and repository metadata from the build context.
