package za.co.talentsearch.ranking.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.talentsearch.ranking.domain.RankedCandidate;
import za.co.talentsearch.scoring.infrastructure.ScoringConfigurationRepository.Configuration;

@Repository
public class RankingRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public RankingRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public void replace(long vacancyId, List<RankedCandidate> results, Configuration configuration) {
    jdbc.update("DELETE FROM ranking_results WHERE vacancy_id=?", vacancyId);
    for (var result : results) {
      try {
        jdbc.update(
            """
            INSERT INTO ranking_results(vacancy_id,candidate_index_id,score,breakdown,explanation,
                candidate_snapshot,scoring_configuration_name,scoring_weights)
            VALUES (?,?,?,?::jsonb,?,?::jsonb,?,?::jsonb)
            """,
            vacancyId,
            result.candidateIndexId(),
            result.score(),
            mapper.writeValueAsString(result.breakdown()),
            result.explanation(),
            mapper.writeValueAsString(result),
            configuration.name(),
            mapper.writeValueAsString(configuration.weights()));
      } catch (JsonProcessingException exception) {
        throw new IllegalStateException("Could not serialize ranking", exception);
      }
    }
  }

  public List<RankedCandidate> find(long vacancyId) {
    return jdbc.query(
        """
        SELECT r.*,c.profile FROM ranking_results r
        JOIN candidate_index c ON c.id=r.candidate_index_id
        WHERE r.vacancy_id=? ORDER BY r.score DESC,r.candidate_index_id
        """,
        (rs, row) -> {
          try {
            String snapshot = rs.getString("candidate_snapshot");
            if (snapshot != null) return mapper.readValue(snapshot, RankedCandidate.class);
            // Historical V1/V2 rankings predate identity snapshots.
            var profile = mapper.readTree(rs.getString("profile"));
            String name = profile.path("name").asText(profile.path("firstName").asText("UNKNOWN"));
            Map<String, Double> breakdown =
                mapper.readValue(rs.getString("breakdown"), new TypeReference<>() {});
            return new RankedCandidate(
                rs.getLong("candidate_index_id"),
                name + " " + profile.path("surname").asText(),
                profile.path("email").asText(),
                rs.getDouble("score"),
                breakdown,
                rs.getString("explanation"));
          } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored ranking", exception);
          }
        },
        vacancyId);
  }
}
