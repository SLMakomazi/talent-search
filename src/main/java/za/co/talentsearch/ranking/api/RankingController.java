package za.co.talentsearch.ranking.api;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import za.co.talentsearch.ranking.application.RankingService;
import za.co.talentsearch.ranking.domain.RankedCandidate;

@RestController
@RequestMapping("/api/vacancies/{vacancyId}/rankings")
public class RankingController {
  private final RankingService service;

  public RankingController(RankingService service) {
    this.service = service;
  }

  @PostMapping
  public List<RankedCandidate> rank(
      @PathVariable long vacancyId, @RequestParam(defaultValue = "default") String configuration) {
    return service.rank(vacancyId, configuration);
  }

  @GetMapping
  public List<RankedCandidate> saved(@PathVariable long vacancyId) {
    return service.saved(vacancyId);
  }
}
