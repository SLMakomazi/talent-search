package za.co.talentsearch.candidate.application;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.talentsearch.candidate.infrastructure.*;
@Service
public class SyncService {
 private final CompanyCandidateConnector connector;
 private final CandidateIndexRepository index;
 private final CvParser parser;
 private final JdbcTemplate jdbc;
 private final TransactionTemplate tx;
 public SyncService(CompanyCandidateConnector connector,CandidateIndexRepository index,CvParser parser,
    JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager tm){
  this.connector=connector;this.index=index;this.parser=parser;this.jdbc=jdbc;this.tx=new TransactionTemplate(tm);
 }
 public SyncResult synchronize(){
  // A transaction-scoped database lock also coordinates manual/scheduled runs across app replicas.
  return tx.execute(transaction->{
   if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(71234001)",Boolean.class)))
    return new SyncResult(0,"ALREADY_RUNNING");
   int total=0;
   while(true){
    var checkpoints=jdbc.query("SELECT last_updated_at,last_source_id FROM sync_checkpoint WHERE source_system='company_mock'",
      (rs,n)->new Checkpoint(rs.getObject(1,OffsetDateTime.class),rs.getString(2)));
    var cp=checkpoints.isEmpty()?new Checkpoint(OffsetDateTime.parse("0001-01-01T00:00:00Z"),""):checkpoints.getFirst();
    var batch=connector.changedAfter(cp.at(),cp.id(),100);
    if(batch.isEmpty())return new SyncResult(total,"COMPLETED");
    for(var candidate:batch){
     String hash=hash(candidate.cvDocument());
     za.co.talentsearch.candidate.domain.CandidateProfile profile;
     try{
      // Identity changes still refresh the JSON even if the PDF bytes are unchanged.
      var cached="application/pdf".equalsIgnoreCase(candidate.cvContentType())?index.unchangedProfile(candidate.candidateNumber(),hash):null;
      profile=cached==null?parser.parse(candidate):new za.co.talentsearch.candidate.domain.CandidateProfile(
       candidate.candidateNumber(),candidate.name(),candidate.surname(),candidate.email(),cached.skills(),
       cached.yearsExperience(),cached.role(),cached.extractionStatus(),cached.experience(),cached.education(),cached.certifications());
      if("PARTIAL".equals(profile.extractionStatus())&&index.hasValidProfile(candidate.candidateNumber()))
       throw new IllegalArgumentException("INCOMPLETE_REPLACEMENT");
     }catch(RuntimeException e){
      String error=e instanceof IllegalArgumentException && e.getMessage()!=null?e.getMessage():"PDF_PROCESSING_FAILED";
      jdbc.update("""
       INSERT INTO candidate_sync_failure(source_system,source_candidate_id,attempted_cv_hash,source_updated_at,error_code)
       VALUES ('company_mock',?,?,?,?) ON CONFLICT(source_system,source_candidate_id) DO UPDATE SET
       attempted_cv_hash=EXCLUDED.attempted_cv_hash,source_updated_at=EXCLUDED.source_updated_at,
       error_code=EXCLUDED.error_code,attempted_at=NOW()
       """,candidate.candidateNumber(),hash,candidate.updatedAt(),error);
      // Do not move past a failure: a subsequent run retries it without skipping a source row.
      return new SyncResult(total,"FAILED_AT_"+candidate.candidateNumber());
     }
     index.upsert(profile,hash,candidate.updatedAt(),true);
     jdbc.update("DELETE FROM candidate_sync_failure WHERE source_system='company_mock' AND source_candidate_id=?",candidate.candidateNumber());
     jdbc.update("""
      INSERT INTO sync_checkpoint(source_system,last_updated_at,last_source_id) VALUES ('company_mock',?,?)
      ON CONFLICT(source_system) DO UPDATE SET last_updated_at=EXCLUDED.last_updated_at,
      last_source_id=EXCLUDED.last_source_id,last_run_at=NOW()
      """,candidate.updatedAt(),candidate.candidateNumber());
     total++;
    }
   }
  });
 }
 private static String hash(byte[] bytes){
  if(bytes==null)return null;
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 private record Checkpoint(OffsetDateTime at,String id){}
 public record SyncResult(int processed,String status){}
}
