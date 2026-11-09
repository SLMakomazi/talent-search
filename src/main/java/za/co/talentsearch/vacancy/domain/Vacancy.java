package za.co.talentsearch.vacancy.domain;

import java.util.List;

public record Vacancy(
    long id,
    String title,
    List<String> requiredSkills,
    List<String> optionalSkills,
    int minYears,
    String location) {}
