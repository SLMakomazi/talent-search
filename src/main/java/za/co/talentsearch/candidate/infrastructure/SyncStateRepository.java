package za.co.talentsearch.candidate.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.talentsearch.candidate.domain.SourceCandidate;

@Repository
public class SyncStateRepository {
  private final JdbcTemplate jdbc;

  public SyncStateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Held by the caller's talent database transaction until commit or rollback. */
  public boolean tryLock() {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(71234001)", Boolean.class));
  }

  public Checkpoint checkpoint() {
    return jdbc
        .query(
            "SELECT last_updated_at,last_source_id,last_run_at FROM sync_checkpoint WHERE"
                + " source_system='company_mock'",
            (rs, row) ->
                new Checkpoint(
                    rs.getObject(1, OffsetDateTime.class),
                    rs.getString(2),
                    rs.getObject(3, OffsetDateTime.class)))
        .stream()
        .findFirst()
        .orElse(new Checkpoint(OffsetDateTime.parse("0001-01-01T00:00:00Z"), "", null));
  }

  public void reset() {
    jdbc.update("DELETE FROM sync_checkpoint WHERE source_system='company_mock'");
  }

  public void accept(SourceCandidate candidate) {
    jdbc.update(
        "DELETE FROM candidate_sync_failure WHERE source_system='company_mock' AND"
            + " source_candidate_id=?",
        candidate.candidateNumber());
    jdbc.update(
        """
INSERT INTO sync_checkpoint(source_system,last_updated_at,last_source_id) VALUES ('company_mock',?,?)
ON CONFLICT(source_system) DO UPDATE SET last_updated_at=EXCLUDED.last_updated_at,
    last_source_id=EXCLUDED.last_source_id,last_run_at=NOW()
""",
        candidate.updatedAt(),
        candidate.candidateNumber());
  }

  public void fail(SourceCandidate candidate, String hash, String errorCode) {
    jdbc.update(
        """
INSERT INTO candidate_sync_failure(source_system,source_candidate_id,attempted_cv_hash,source_updated_at,error_code)
VALUES ('company_mock',?,?,?,?) ON CONFLICT(source_system,source_candidate_id) DO UPDATE SET
    attempted_cv_hash=EXCLUDED.attempted_cv_hash,source_updated_at=EXCLUDED.source_updated_at,
    error_code=EXCLUDED.error_code,attempted_at=NOW()
""",
        candidate.candidateNumber(),
        hash,
        candidate.updatedAt(),
        errorCode);
  }

  public List<Failure> failures() {
    return jdbc.query(
        """
        SELECT source_candidate_id,attempted_cv_hash,source_updated_at,error_code,attempted_at
        FROM candidate_sync_failure WHERE source_system='company_mock' ORDER BY source_candidate_id
        """,
        (rs, row) ->
            new Failure(
                rs.getString(1),
                rs.getString(2),
                rs.getObject(3, OffsetDateTime.class),
                rs.getString(4),
                rs.getObject(5, OffsetDateTime.class)));
  }

  public record Checkpoint(
      OffsetDateTime at, String candidateNumber, OffsetDateTime lastSuccessfulRunAt) {}

  public record Failure(
      String candidateNumber,
      String attemptedCvHash,
      OffsetDateTime sourceUpdatedAt,
      String errorCode,
      OffsetDateTime attemptedAt) {}
}
