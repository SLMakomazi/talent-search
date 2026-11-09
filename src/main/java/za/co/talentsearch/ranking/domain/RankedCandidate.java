package za.co.talentsearch.ranking.domain;
import java.util.Map;
public record RankedCandidate(long candidateIndexId,String name,String email,double score,Map<String,Double> breakdown,String explanation){}
