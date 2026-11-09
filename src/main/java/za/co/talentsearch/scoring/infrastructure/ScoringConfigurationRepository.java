package za.co.talentsearch.scoring.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import za.co.talentsearch.scoring.domain.ScoreWeights;

@Repository
public class ScoringConfigurationRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public ScoringConfigurationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public List<Configuration> list() {
    return jdbc.query(
        "SELECT name,weights::text,updated_at FROM scoring_configuration ORDER BY name",
        (rs, row) ->
            new Configuration(
                rs.getString("name"),
                read(rs.getString("weights")),
                rs.getObject("updated_at", OffsetDateTime.class)));
  }

  public Configuration get(String name) {
    return jdbc
        .query(
            "SELECT name,weights::text,updated_at FROM scoring_configuration WHERE name=?",
            (rs, row) ->
                new Configuration(
                    rs.getString("name"),
                    read(rs.getString("weights")),
                    rs.getObject("updated_at", OffsetDateTime.class)),
            name)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Scoring configuration not found"));
  }

  public Configuration save(String name, ScoreWeights weights) {
    if (!weights.isTotalValid()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Scoring weights must add up to 100");
    }
    try {
      // RETURNING ensures the response is the configuration written by this statement.
      return jdbc.queryForObject(
          """
          INSERT INTO scoring_configuration(name,weights) VALUES (?,?::jsonb)
          ON CONFLICT(name) DO UPDATE SET weights=EXCLUDED.weights,updated_at=NOW()
          RETURNING name,weights::text,updated_at
          """,
          (rs, row) ->
              new Configuration(
                  rs.getString("name"),
                  read(rs.getString("weights")),
                  rs.getObject("updated_at", OffsetDateTime.class)),
          name,
          mapper.writeValueAsString(weights));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize scoring weights", exception);
    }
  }

  private ScoreWeights read(String json) {
    try {
      return mapper.readValue(json, ScoreWeights.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid stored scoring weights", exception);
    }
  }

  public record Configuration(String name, ScoreWeights weights, OffsetDateTime updatedAt) {}
}
