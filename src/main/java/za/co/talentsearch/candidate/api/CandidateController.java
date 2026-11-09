package za.co.talentsearch.candidate.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.talentsearch.candidate.application.SyncService;
import za.co.talentsearch.candidate.infrastructure.CandidateIndexRepository;
import za.co.talentsearch.candidate.infrastructure.SyncStateRepository;

@RestController
@Validated
@RequestMapping("/api/candidates")
public class CandidateController {
  private final SyncService sync;
  private final CandidateIndexRepository index;
  private final SyncStateRepository state;

  public CandidateController(
      SyncService sync, CandidateIndexRepository index, SyncStateRepository state) {
    this.sync = sync;
    this.index = index;
    this.state = state;
  }

  @PostMapping("/sync")
  public SyncService.SyncResult sync(@RequestParam(defaultValue = "false") boolean full) {
    return sync.synchronize(full);
  }

  @GetMapping("/sync/status")
  public SyncStatus status() {
    return new SyncStatus(state.checkpoint(), state.failures());
  }

  @GetMapping
  public List<CandidateIndexRepository.IndexedCandidate> search(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String skill,
      @RequestParam(required = false) @Min(0) Integer minYears,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return index.search(query.trim(), skill.trim(), minYears, limit, offset);
  }

  @GetMapping("/{id}")
  public CandidateIndexRepository.IndexedCandidate get(@PathVariable long id) {
    return index.get(id);
  }

  public record SyncStatus(
      SyncStateRepository.Checkpoint checkpoint, List<SyncStateRepository.Failure> failures) {}
}
