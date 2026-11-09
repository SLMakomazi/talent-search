package za.co.talentsearch.candidate.domain;
import java.util.List;
public record CandidateProfile(String candidateNumber, String name, String surname, String email,
 List<String> skills, Integer yearsExperience, String role, String extractionStatus,
 List<Experience> experience, List<String> education, List<String> certifications) {
 public record Experience(String jobTitle, String company, String startDate, String endDate) {}
 // Preserve the existing Java caller contract; JSON uses candidateNumber and name.
 public CandidateProfile(String id,String name,String surname,String email,List<String> skills,
   int years,String role,String status) {
  this(id,name,surname,email,skills,years,role,status,List.of(),List.of("UNKNOWN"),List.of("UNKNOWN"));
 }
 public String sourceCandidateId(){return candidateNumber;}
 public String firstName(){return name;}
}
