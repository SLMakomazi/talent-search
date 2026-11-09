package za.co.talentsearch.candidate.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.talentsearch.candidate.domain.CandidateProfile;
import za.co.talentsearch.candidate.domain.SourceCandidate;
import za.co.talentsearch.candidate.infrastructure.*;

@Service
public class SyncService {
  private static final Logger LOG = LoggerFactory.getLogger(SyncService.class);
  private final CompanyCandidateConnector connector;
  private final CandidateIndexRepository index;
  private final CvParser parser;
  private final SyncStateRepository state;
  private final TransactionTemplate transaction;

  public SyncService(
      CompanyCandidateConnector connector,
      CandidateIndexRepository index,
      CvParser parser,
      SyncStateRepository state,
      PlatformTransactionManager transactionManager) {
    this.connector = connector;
    this.index = index;
    this.parser = parser;
    this.state = state;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public SyncResult synchronize() {
    return synchronize(false);
  }

  public SyncResult synchronize(boolean full) {
    return transaction.execute(
        status -> {
          if (!state.tryLock()) return new SyncResult(0, "ALREADY_RUNNING");
          if (full) state.reset();
          int processed = 0;
          while (true) {
            var checkpoint = state.checkpoint();
            var batch = connector.changedAfter(checkpoint.at(), checkpoint.candidateNumber(), 100);
            if (batch.isEmpty()) {
              LOG.info("Candidate synchronization completed: {} profiles accepted", processed);
              return new SyncResult(processed, "COMPLETED");
            }
            for (var candidate : batch) {
              String hash = hash(candidate.cvDocument());
              CandidateProfile profile;
              try {
                profile = extract(candidate, hash, full);
                if ("PARTIAL".equals(profile.extractionStatus())
                    && index.hasValidProfile(candidate.candidateNumber())) {
                  throw new CvProcessingException("INCOMPLETE_REPLACEMENT");
                }
              } catch (CvProcessingException exception) {
                state.fail(candidate, hash, exception.getMessage());
                LOG.warn(
                    "Candidate {} could not be processed: {}",
                    candidate.candidateNumber(),
                    exception.getMessage());
                // Commit earlier successes and the failure, but never skip this failed source
                // position.
                return new SyncResult(processed, "FAILED_AT_" + candidate.candidateNumber());
              }
              index.upsert(profile, hash, candidate.updatedAt(), true);
              state.accept(candidate);
              processed++;
            }
          }
        });
  }

  private CandidateProfile extract(SourceCandidate source, String hash, boolean full) {
    var cached =
        !full && "application/pdf".equalsIgnoreCase(source.cvContentType())
            ? index.unchangedProfile(source.candidateNumber(), hash)
            : null;
    if (cached == null) return parser.parse(source);
    // Candidate identity may change independently of the document bytes.
    return new CandidateProfile(
        source.candidateNumber(),
        source.name(),
        source.surname(),
        source.email(),
        cached.skills(),
        cached.yearsExperience(),
        cached.role(),
        cached.extractionStatus(),
        cached.experience(),
        cached.education(),
        cached.certifications());
  }

  private static String hash(byte[] bytes) {
    if (bytes == null) return null;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record SyncResult(int processed, String status) {}
}
