package za.co.talentsearch.vacancy.api;
import jakarta.validation.constraints.*;
import java.util.List;
public record CreateVacancyRequest(@NotBlank String title,@NotNull List<String> requiredSkills,
 List<String> optionalSkills,@Min(0) int minYears,String location){}
