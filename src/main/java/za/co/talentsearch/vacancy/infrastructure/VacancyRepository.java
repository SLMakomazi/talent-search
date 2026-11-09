package za.co.talentsearch.vacancy.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import za.co.talentsearch.vacancy.api.CreateVacancyRequest;
import za.co.talentsearch.vacancy.domain.Vacancy;

@Repository
public class VacancyRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public VacancyRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public long create(CreateVacancyRequest request) {
    try {
      String required = mapper.writeValueAsString(request.requiredSkills());
      String optional =
          mapper.writeValueAsString(
              request.optionalSkills() == null ? List.of() : request.optionalSkills());
      var key = new GeneratedKeyHolder();
      jdbc.update(
          connection -> {
            var statement =
                connection.prepareStatement(
                    """
                    INSERT INTO vacancies(title,required_skills,optional_skills,min_years,location)
                    VALUES (?,?::jsonb,?::jsonb,?,?)
                    """,
                    new String[] {"id"});
            statement.setString(1, request.title());
            statement.setString(2, required);
            statement.setString(3, optional);
            statement.setInt(4, request.minYears());
            statement.setString(5, request.location());
            return statement;
          },
          key);
      return Objects.requireNonNull(key.getKey()).longValue();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize vacancy skills", exception);
    }
  }

  public List<Vacancy> list(int limit, int offset) {
    return jdbc.query(
        "SELECT * FROM vacancies ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
        (rs, row) -> map(rs),
        limit,
        offset);
  }

  public Vacancy get(long id) {
    return jdbc.query("SELECT * FROM vacancies WHERE id=?", (rs, row) -> map(rs), id).stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vacancy not found"));
  }

  private Vacancy map(ResultSet result) throws SQLException {
    try {
      List<String> required =
          mapper.readValue(result.getString("required_skills"), new TypeReference<>() {});
      List<String> optional =
          mapper.readValue(result.getString("optional_skills"), new TypeReference<>() {});
      return new Vacancy(
          result.getLong("id"),
          result.getString("title"),
          required,
          optional,
          result.getInt("min_years"),
          result.getString("location"));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid stored vacancy", exception);
    }
  }
}
