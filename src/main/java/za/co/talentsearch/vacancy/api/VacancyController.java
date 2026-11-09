package za.co.talentsearch.vacancy.api;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import za.co.talentsearch.vacancy.infrastructure.VacancyRepository;
import za.co.talentsearch.vacancy.domain.Vacancy;
@RestController @RequestMapping("/api/vacancies")
public class VacancyController {
 private final VacancyRepository repository;
 public VacancyController(VacancyRepository repository){this.repository=repository;}
 @PostMapping public Map<String,Long> create(@Valid @RequestBody CreateVacancyRequest request){return Map.of("id",repository.create(request));}
 @GetMapping("/{id}") public Vacancy get(@PathVariable long id){return repository.get(id);}
}
