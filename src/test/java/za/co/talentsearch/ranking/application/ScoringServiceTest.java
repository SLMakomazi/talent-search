package za.co.talentsearch.ranking.application;
import org.junit.jupiter.api.Test;
import java.util.List;
import za.co.talentsearch.candidate.domain.CandidateProfile;
import za.co.talentsearch.vacancy.domain.Vacancy;
import static org.assertj.core.api.Assertions.assertThat;
class ScoringServiceTest {
 @Test void scoresMatchingCandidate() {
  var candidate=new CandidateProfile("101","John","Smith","john@example.com",List.of("Java","Spring Boot","AWS"),4,"Backend Developer","PARSED");
  var vacancy=new Vacancy(1,"Backend Developer",List.of("Java","Spring Boot"),List.of("AWS"),3,"Johannesburg");
  var result=new ScoringService().score(1,candidate,vacancy);
  assertThat(result.score()).isEqualTo(90.0);
 }
}
