package za.co.talentsearch.ranking.application;

import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.talentsearch.candidate.infrastructure.CandidateIndexRepository;
import za.co.talentsearch.ranking.domain.RankedCandidate;
import za.co.talentsearch.ranking.infrastructure.RankingRepository;
import za.co.talentsearch.scoring.infrastructure.ScoringConfigurationRepository;
import za.co.talentsearch.vacancy.infrastructure.VacancyRepository;

@Service
public class RankingService {
  private final CandidateIndexRepository candidates;
  private final VacancyRepository vacancies;
  private final ScoringService scorer;
  private final RankingRepository rankings;
  private final ScoringConfigurationRepository configurations;
  private final JdbcTemplate jdbc;

  public RankingService(
      CandidateIndexRepository candidates,
      VacancyRepository vacancies,
      ScoringService scorer,
      RankingRepository rankings,
      ScoringConfigurationRepository configurations,
      JdbcTemplate jdbc) {
    this.candidates = candidates;
    this.vacancies = vacancies;
    this.scorer = scorer;
    this.rankings = rankings;
    this.configurations = configurations;
    this.jdbc = jdbc;
  }

  @Transactional
  public List<RankedCandidate> rank(long vacancyId, String configurationName) {
    var vacancy = vacancies.get(vacancyId);
    // Serialize refreshes of one vacancy, preventing overlapping DELETE/INSERT operations.
    jdbc.queryForObject("SELECT id FROM vacancies WHERE id=? FOR UPDATE", Long.class, vacancyId);
    var configuration = configurations.get(configurationName);
    var results =
        candidates.activeCandidates().stream()
            .map(
                candidate ->
                    scorer.score(
                        candidate.id(), candidate.profile(), vacancy, configuration.weights()))
            .sorted(
                Comparator.comparingDouble(RankedCandidate::score)
                    .reversed()
                    .thenComparingLong(RankedCandidate::candidateIndexId))
            .toList();
    rankings.replace(vacancyId, results, configuration);
    return results;
  }

  @Transactional
  public List<RankedCandidate> rank(long vacancyId) {
    return rank(vacancyId, "default");
  }

  public List<RankedCandidate> saved(long vacancyId) {
    vacancies.get(vacancyId);
    return rankings.find(vacancyId);
  }
}
