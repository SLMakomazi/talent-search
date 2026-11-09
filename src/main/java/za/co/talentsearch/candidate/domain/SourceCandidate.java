package za.co.talentsearch.candidate.domain;
import java.time.OffsetDateTime;
public record SourceCandidate(String candidateNumber, String name, String surname, String email,
 byte[] cvDocument, String cvFilename, String cvContentType, OffsetDateTime updatedAt) {}
