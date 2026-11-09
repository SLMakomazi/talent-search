package za.co.talentsearch.candidate.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import za.co.talentsearch.candidate.domain.CandidateProfile;

@Repository
public class CandidateIndexRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public CandidateIndexRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void upsert(
      CandidateProfile profile, String hash, OffsetDateTime changed, boolean active) {
    try {
      jdbc.update(
          """
INSERT INTO candidate_index(source_system,source_candidate_id,profile,cv_hash,source_updated_at,active,extraction_status)
VALUES ('company_mock',?,?::jsonb,?,?,?,?)
ON CONFLICT(source_system,source_candidate_id) DO UPDATE SET
    profile=EXCLUDED.profile,cv_hash=EXCLUDED.cv_hash,source_updated_at=EXCLUDED.source_updated_at,
    active=EXCLUDED.active,indexed_at=NOW(),extraction_status=EXCLUDED.extraction_status
""",
          profile.candidateNumber(),
          mapper.writeValueAsString(profile),
          hash,
          changed,
          active,
          profile.extractionStatus());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize candidate profile", exception);
    }
  }

  public CandidateProfile unchangedProfile(String number, String hash) {
    var rows =
        jdbc.query(
            """
SELECT profile::text FROM candidate_index
WHERE source_system='company_mock' AND source_candidate_id=? AND cv_hash=? AND active=true
""",
            (rs, row) -> read(rs.getString("profile")),
            number,
            hash);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public boolean hasValidProfile(String number) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS(SELECT 1 FROM candidate_index WHERE source_system='company_mock'
                AND source_candidate_id=? AND active=true AND extraction_status='PARSED')
            """,
            Boolean.class,
            number));
  }

  public List<IndexedCandidate> activeCandidates() {
    return jdbc.query(
        "SELECT id,profile::text FROM candidate_index WHERE active=true ORDER BY id",
        (rs, row) -> new IndexedCandidate(rs.getLong("id"), read(rs.getString("profile"))));
  }

  public List<IndexedCandidate> search(
      String query, String skill, Integer minYears, int limit, int offset) {
    // Parameters are bound values. JSONB values come exclusively from accepted CV extraction.
    return jdbc.query(
        """
SELECT id,profile::text FROM candidate_index
WHERE active=true
    AND (?='' OR strpos(lower(concat_ws(' ',profile->>'candidateNumber',profile->>'name',
        profile->>'surname',profile->>'email',profile->>'role')),lower(?))>0)
    AND (?='' OR EXISTS(SELECT 1 FROM jsonb_array_elements_text(profile->'skills') AS s(value)
        WHERE lower(s.value)=lower(?)))
    AND (CAST(? AS integer) IS NULL OR (profile->>'yearsExperience')::integer >= ?)
ORDER BY source_candidate_id,id LIMIT ? OFFSET ?
""",
        (rs, row) -> new IndexedCandidate(rs.getLong("id"), read(rs.getString("profile"))),
        query,
        query,
        skill,
        skill,
        minYears,
        minYears,
        limit,
        offset);
  }

  public IndexedCandidate get(long id) {
    return jdbc
        .query(
            "SELECT id,profile::text FROM candidate_index WHERE id=? AND active=true",
            (rs, row) -> new IndexedCandidate(rs.getLong("id"), read(rs.getString("profile"))),
            id)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
  }

  private CandidateProfile read(String json) {
    try {
      return mapper.readValue(json, CandidateProfile.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid stored candidate profile", exception);
    }
  }

  public record IndexedCandidate(long id, CandidateProfile profile) {}
}
