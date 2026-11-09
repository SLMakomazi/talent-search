package za.co.talentsearch.vacancy.api;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateVacancyRequest(
    @NotBlank @Size(max = 255) String title,
    @NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> requiredSkills,
    @Size(max = 100) List<@NotBlank @Size(max = 100) String> optionalSkills,
    @Min(0) @Max(100) int minYears,
    @Size(max = 255) String location) {

  public CreateVacancyRequest {
    if (title != null) title = title.trim();
    if (requiredSkills != null)
      requiredSkills = requiredSkills.stream().map(s -> s == null ? null : s.trim()).toList();
    if (optionalSkills != null)
      optionalSkills = optionalSkills.stream().map(s -> s == null ? null : s.trim()).toList();
  }
}
