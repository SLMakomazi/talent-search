package za.co.talentsearch.scoring.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.talentsearch.scoring.domain.ScoreWeights;
import za.co.talentsearch.scoring.infrastructure.ScoringConfigurationRepository;
import za.co.talentsearch.scoring.infrastructure.ScoringConfigurationRepository.Configuration;

@RestController
@Validated
@RequestMapping("/api/scoring-configurations")
public class ScoringConfigurationController {
  private final ScoringConfigurationRepository configurations;

  public ScoringConfigurationController(ScoringConfigurationRepository configurations) {
    this.configurations = configurations;
  }

  @GetMapping
  public List<Configuration> list() {
    return configurations.list();
  }

  @GetMapping("/{name}")
  public Configuration get(@PathVariable String name) {
    return configurations.get(name);
  }

  @PutMapping("/{name}")
  public Configuration save(
      @PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{1,100}") String name,
      @Valid @RequestBody ScoreWeights weights) {
    return configurations.save(name, weights);
  }
}
