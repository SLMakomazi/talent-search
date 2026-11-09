package za.co.talentsearch.vacancy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.talentsearch.vacancy.domain.Vacancy;
import za.co.talentsearch.vacancy.infrastructure.VacancyRepository;

@RestController
@Validated
@RequestMapping("/api/vacancies")
public class VacancyController {
  private final VacancyRepository repository;

  public VacancyController(VacancyRepository repository) {
    this.repository = repository;
  }

  @PostMapping
  public Map<String, Long> create(@Valid @RequestBody CreateVacancyRequest request) {
    return Map.of("id", repository.create(request));
  }

  @GetMapping
  public List<Vacancy> list(
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return repository.list(limit, offset);
  }

  @GetMapping("/{id}")
  public Vacancy get(@PathVariable long id) {
    return repository.get(id);
  }
}
