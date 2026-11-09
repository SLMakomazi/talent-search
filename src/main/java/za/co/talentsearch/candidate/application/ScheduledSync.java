package za.co.talentsearch.candidate.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledSync {
  private final SyncService service;
  private final boolean enabled;

  public ScheduledSync(SyncService service, @Value("${app.sync.enabled:true}") boolean enabled) {
    this.service = service;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${app.sync.fixed-delay-ms:900000}", initialDelay = 10000)
  public void sync() {
    if (enabled) service.synchronize();
  }
}
