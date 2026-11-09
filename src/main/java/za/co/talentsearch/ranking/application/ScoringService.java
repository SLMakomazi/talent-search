package za.co.talentsearch.ranking.application;

import java.util.*;
import org.springframework.stereotype.Service;
import za.co.talentsearch.candidate.domain.CandidateProfile;
import za.co.talentsearch.ranking.domain.RankedCandidate;
import za.co.talentsearch.scoring.domain.ScoreWeights;
import za.co.talentsearch.vacancy.domain.Vacancy;

@Service
public class ScoringService {
  /** Retains the original scoring contract for callers using the default weights. */
  public RankedCandidate score(long candidateId, CandidateProfile candidate, Vacancy vacancy) {
    return score(candidateId, candidate, vacancy, ScoreWeights.defaults());
  }

  public RankedCandidate score(
      long candidateId, CandidateProfile candidate, Vacancy vacancy, ScoreWeights weights) {
    List<String> requiredMatches = matches(candidate.skills(), vacancy.requiredSkills());
    double required =
        ratio(requiredMatches, vacancy.requiredSkills()) * weights.requiredSkillsWeight();
    double experience =
        candidate.yearsExperience() == null
            ? 0
            : vacancy.minYears() == 0
                ? weights.experienceWeight()
                : Math.min(1.0, (double) candidate.yearsExperience() / vacancy.minYears())
                    * weights.experienceWeight();
    double role = overlap(candidate.role(), vacancy.title()) ? weights.roleWeight() : 0;
    double optional =
        ratio(matches(candidate.skills(), vacancy.optionalSkills()), vacancy.optionalSkills())
            * weights.optionalSkillsWeight();

    Map<String, Double> breakdown = new LinkedHashMap<>();
    breakdown.put("requiredSkills", round(required));
    breakdown.put("experience", round(experience));
    breakdown.put("roleMatch", round(role));
    breakdown.put("optionalSkills", round(optional));
    // No CV-derived location or availability evidence is available in this demo.
    breakdown.put("locationAvailability", 0.0);
    String explanation =
        "Matched required skills: "
            + requiredMatches
            + "; years of experience: "
            + (candidate.yearsExperience() == null ? "UNKNOWN" : candidate.yearsExperience())
            + "; role: "
            + candidate.role()
            + ". Location/availability not provided.";
    return new RankedCandidate(
        candidateId,
        candidate.name() + " " + candidate.surname(),
        candidate.email(),
        round(required + experience + role + optional),
        breakdown,
        explanation);
  }

  private double ratio(List<String> matched, List<String> target) {
    if (target == null || target.isEmpty()) return 0;
    long distinctTargets = target.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count();
    return (double) matched.size() / distinctTargets;
  }

  private List<String> matches(List<String> actual, List<String> target) {
    if (target == null || actual == null) return List.of();
    Map<String, String> distinct = new LinkedHashMap<>();
    target.stream()
        .filter(t -> actual.stream().anyMatch(a -> a.equalsIgnoreCase(t)))
        .forEach(t -> distinct.putIfAbsent(t.toLowerCase(Locale.ROOT), t));
    return List.copyOf(distinct.values());
  }

  private boolean overlap(String actual, String requested) {
    if (actual == null || requested == null || "UNKNOWN".equals(actual)) return false;
    var left =
        Arrays.stream(actual.toLowerCase(Locale.ROOT).split("\\W+"))
            .filter(word -> word.length() > 3)
            .toList();
    var right =
        Arrays.stream(requested.toLowerCase(Locale.ROOT).split("\\W+"))
            .filter(word -> word.length() > 3)
            .toList();
    return left.stream().anyMatch(right::contains);
  }

  private double round(double number) {
    return Math.round(number * 100) / 100.0;
  }
}
