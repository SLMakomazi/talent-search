package za.co.talentsearch.scoring.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** The available points for each scoring factor. Unknown evidence earns zero points. */
public record ScoreWeights(
    @Min(0) @Max(100) int requiredSkillsWeight,
    @Min(0) @Max(100) int experienceWeight,
    @Min(0) @Max(100) int roleWeight,
    @Min(0) @Max(100) int optionalSkillsWeight,
    @Min(0) @Max(100) int locationAvailabilityWeight) {

  public static ScoreWeights defaults() {
    return new ScoreWeights(40, 25, 15, 10, 10);
  }

  @JsonIgnore
  @AssertTrue(message = "Scoring weights must add up to 100")
  public boolean isTotalValid() {
    return requiredSkillsWeight
            + experienceWeight
            + roleWeight
            + optionalSkillsWeight
            + locationAvailabilityWeight
        == 100;
  }
}
