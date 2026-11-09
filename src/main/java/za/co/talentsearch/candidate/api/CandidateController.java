package za.co.talentsearch.candidate.api;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import za.co.talentsearch.candidate.application.SyncService;
import za.co.talentsearch.candidate.infrastructure.CandidateIndexRepository;
@RestController @RequestMapping("/api/candidates")
public class CandidateController {
 private final SyncService sync;private final CandidateIndexRepository index;
 public CandidateController(SyncService sync,CandidateIndexRepository index){this.sync=sync;this.index=index;}
 @PostMapping("/sync") public SyncService.SyncResult sync(){return sync.synchronize();}
 @GetMapping public List<CandidateIndexRepository.IndexedCandidate> list(){return index.activeCandidates();}
}
