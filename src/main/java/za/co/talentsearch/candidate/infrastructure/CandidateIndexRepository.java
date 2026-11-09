package za.co.talentsearch.candidate.infrastructure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.talentsearch.candidate.domain.CandidateProfile;
@Repository
public class CandidateIndexRepository {
 private final JdbcTemplate jdbc; private final ObjectMapper mapper;
 public CandidateIndexRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public void upsert(CandidateProfile profile,String hash,OffsetDateTime changed,boolean active){
   try {jdbc.update("""
    INSERT INTO candidate_index(source_system,source_candidate_id,profile,cv_hash,source_updated_at,active,extraction_status)
    VALUES ('company_mock',?,?::jsonb,?,?,?,?)
    ON CONFLICT(source_system,source_candidate_id) DO UPDATE SET
    profile=EXCLUDED.profile,cv_hash=EXCLUDED.cv_hash,source_updated_at=EXCLUDED.source_updated_at,
    active=EXCLUDED.active,indexed_at=NOW(),extraction_status=EXCLUDED.extraction_status
    """,profile.sourceCandidateId(),mapper.writeValueAsString(profile),hash,changed,active,profile.extractionStatus());
   }catch(JsonProcessingException e){throw new IllegalStateException("JSON serialization failed",e);}
 }
 public CandidateProfile unchangedProfile(String number,String hash){
  var rows=jdbc.query("SELECT profile::text FROM candidate_index WHERE source_system='company_mock' AND source_candidate_id=? AND cv_hash=? AND active=true",(rs,n)->{
   try{return mapper.readValue(rs.getString(1),CandidateProfile.class);}
   catch(JsonProcessingException e){throw new IllegalStateException(e);}
  },number,hash);
  return rows.isEmpty()?null:rows.getFirst();
 }
 public boolean hasValidProfile(String number){
  return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM candidate_index WHERE source_system='company_mock' AND source_candidate_id=? AND active=true AND extraction_status='PARSED')",Boolean.class,number));
 }
 public List<IndexedCandidate> activeCandidates(){return jdbc.query("SELECT id,profile::text AS profile FROM candidate_index WHERE active=true",(rs,n)->{
   try{return new IndexedCandidate(rs.getLong("id"),mapper.readValue(rs.getString("profile"),CandidateProfile.class));}
   catch(JsonProcessingException e){throw new IllegalStateException(e);}
 });}
 public record IndexedCandidate(long id,CandidateProfile profile){}
}
