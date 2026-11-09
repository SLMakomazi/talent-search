package za.co.talentsearch.vacancy.infrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;
import za.co.talentsearch.vacancy.api.CreateVacancyRequest;
import za.co.talentsearch.vacancy.domain.Vacancy;
@Repository
public class VacancyRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public VacancyRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public long create(CreateVacancyRequest request){
  try {
   String required=mapper.writeValueAsString(request.requiredSkills());
   String optional=mapper.writeValueAsString(request.optionalSkills()==null?List.of():request.optionalSkills());
   KeyHolder key=new GeneratedKeyHolder();
   jdbc.update(con->{var ps=con.prepareStatement("INSERT INTO vacancies(title,required_skills,optional_skills,min_years,location) VALUES (?,?::jsonb,?::jsonb,?,?)",new String[]{"id"});
    ps.setString(1,request.title());ps.setString(2,required);ps.setString(3,optional);ps.setInt(4,request.minYears());ps.setString(5,request.location());return ps;},key);
   return Objects.requireNonNull(key.getKey()).longValue();
  }catch(JsonProcessingException e){throw new IllegalStateException(e);}
 }
 public Vacancy get(long id){return jdbc.query("SELECT * FROM vacancies WHERE id=?",rs->{
  if(!rs.next())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Vacancy not found");
  try {return new Vacancy(rs.getLong("id"),rs.getString("title"),mapper.readValue(rs.getString("required_skills"),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){}),mapper.readValue(rs.getString("optional_skills"),new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){}),rs.getInt("min_years"),rs.getString("location"));}
  catch(JsonProcessingException e){throw new IllegalStateException(e);}
 },id);}
}
