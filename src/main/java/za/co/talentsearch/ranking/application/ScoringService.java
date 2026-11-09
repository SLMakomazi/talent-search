package za.co.talentsearch.ranking.application;
import java.util.*;
import org.springframework.stereotype.Service;
import za.co.talentsearch.candidate.domain.CandidateProfile;
import za.co.talentsearch.ranking.domain.RankedCandidate;
import za.co.talentsearch.vacancy.domain.Vacancy;
@Service
public class ScoringService {
 public RankedCandidate score(long candidateId,CandidateProfile c,Vacancy v){
  double required=ratio(c.skills(),v.requiredSkills())*40;
  double experience=c.yearsExperience()==null?0:v.minYears()==0?25:Math.min(1.0,(double)c.yearsExperience()/v.minYears())*25;
  double role=overlap(c.role(),v.title())?15:0;
  double optional=ratio(c.skills(),v.optionalSkills())*10;
  // Location/availability not present in source: demo excludes these 10 points rather than assuming a match.
  Map<String,Double> breakdown=new LinkedHashMap<>();
  breakdown.put("requiredSkills",round(required));breakdown.put("experience",round(experience));breakdown.put("roleMatch",round(role));breakdown.put("optionalSkills",round(optional));breakdown.put("locationAvailability",0.0);
  double score=round(required+experience+role+optional);
  String explanation="Matched required skills: "+matches(c.skills(),v.requiredSkills())+"; years of experience: "+(c.yearsExperience()==null?"UNKNOWN":c.yearsExperience())+"; role: "+c.role()+". Location/availability not provided.";
  return new RankedCandidate(candidateId,c.firstName()+" "+c.surname(),c.email(),score,breakdown,explanation);
 }
 private double ratio(List<String> actual,List<String> target){if(target==null||target.isEmpty())return 0;return (double)matches(actual,target).size()/target.size();}
 private List<String> matches(List<String> actual,List<String> target){if(target==null)return List.of();return target.stream().filter(t->actual.stream().anyMatch(a->a.equalsIgnoreCase(t))).toList();}
 private boolean overlap(String a,String b){if(a==null||b==null)return false;var left=Arrays.stream(a.toLowerCase().split("\\W+")).filter(x->x.length()>3).toList();var right=Arrays.stream(b.toLowerCase().split("\\W+")).filter(x->x.length()>3).toList();return left.stream().anyMatch(right::contains);}
 private double round(double n){return Math.round(n*100)/100.0;}
}
