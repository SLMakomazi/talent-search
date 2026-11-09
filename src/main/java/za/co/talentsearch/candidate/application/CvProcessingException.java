package za.co.talentsearch.candidate.application;

/**
 * A recoverable document failure; database failures must propagate and roll back synchronization.
 */
public class CvProcessingException extends RuntimeException {
  public CvProcessingException(String code) {
    super(code);
  }

  public CvProcessingException(String code, Throwable cause) {
    super(code, cause);
  }
}
