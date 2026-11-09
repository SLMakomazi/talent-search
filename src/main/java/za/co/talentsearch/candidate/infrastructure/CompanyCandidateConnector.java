package za.co.talentsearch.candidate.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.talentsearch.candidate.domain.SourceCandidate;

@Repository
public class CompanyCandidateConnector {
  private final JdbcTemplate source;

  public CompanyCandidateConnector(@Qualifier("sourceJdbc") JdbcTemplate source) {
    this.source = source;
  }

  public List<SourceCandidate> changedAfter(OffsetDateTime time, String id, int limit) {
    return source.query(
        """
SELECT candidate_number,name,surname,email,cv_document,cv_filename,cv_content_type,updated_at
FROM candidates
WHERE updated_at > ? OR (updated_at = ? AND candidate_number COLLATE "C" > ?)
ORDER BY updated_at,candidate_number COLLATE "C" LIMIT ?
""",
        (rs, n) ->
            new SourceCandidate(
                rs.getString("candidate_number"),
                rs.getString("name"),
                rs.getString("surname"),
                rs.getString("email"),
                rs.getBytes("cv_document"),
                rs.getString("cv_filename"),
                rs.getString("cv_content_type"),
                rs.getObject("updated_at", OffsetDateTime.class)),
        time,
        time,
        id,
        limit);
  }
}
