package za.co.talentsearch.ranking.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.talentsearch.candidate.infrastructure.CandidateIndexRepository;
import za.co.talentsearch.ranking.domain.RankedCandidate;
import za.co.talentsearch.vacancy.infrastructure.VacancyRepository;
@Service
public class RankingService {
 private final CandidateIndexRepository candidates;private final VacancyRepository vacancies;private final ScoringService scorer;private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public RankingService(CandidateIndexRepository c,VacancyRepository v,ScoringService s,JdbcTemplate jdbc,ObjectMapper mapper){candidates=c;vacancies=v;scorer=s;this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional public List<RankedCandidate> rank(long vacancyId){
  var vacancy=vacancies.get(vacancyId);
  var results=candidates.activeCandidates().stream().map(c->scorer.score(c.id(),c.profile(),vacancy)).sorted(Comparator.comparingDouble(RankedCandidate::score).reversed()).toList();
  jdbc.update("DELETE FROM ranking_results WHERE vacancy_id=?",vacancyId);
  for(var r:results){try{jdbc.update("INSERT INTO ranking_results(vacancy_id,candidate_index_id,score,breakdown,explanation) VALUES (?,?,?,?::jsonb,?)",vacancyId,r.candidateIndexId(),r.score(),mapper.writeValueAsString(r.breakdown()),r.explanation());}catch(Exception e){throw new IllegalStateException(e);}}
  return results;
 }
}
