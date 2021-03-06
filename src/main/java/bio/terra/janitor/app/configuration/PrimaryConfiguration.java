package bio.terra.janitor.app.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "janitor.primary")
public class PrimaryConfiguration {
  /** Whether to run the scheduler to periodically clean flights. */
  private boolean schedulerEnabled;

  /**
   * The maximum number of unsubmitted flights to recover at start up time. This is to prevent
   * blowing out the service at start up, but if we have too many flights to recover, they will not
   * all be recovered immediately.
   */
  private int unsubmittedFlightRecoveryLimit = 1000;

  /** How often to query for flights to submit. */
  private Duration flightSubmissionPeriod = Duration.ofMinutes(1);

  /** How often to query for flights that have been completed. */
  private Duration flightCompletionPeriod = Duration.ofMinutes(1);

  /** How many flights to process for completion at a time. */
  private int flightCompletionLimit = 1000;

  /** How often to query for flights that have completed fatally. */
  private Duration fatalFlightCompletionPeriod = Duration.ofMinutes(1);

  /** How many fatal flights to process for completion at a time. */
  private int fatalFlightCompletionLimit = 1000;

  /** How often to record the counts of the different resources in the database. */
  private Duration recordResourceCountPeriod = Duration.ofMinutes(10);

  public boolean isSchedulerEnabled() {
    return schedulerEnabled;
  }

  public int getUnsubmittedFlightRecoveryLimit() {
    return unsubmittedFlightRecoveryLimit;
  }

  public Duration getFlightSubmissionPeriod() {
    return flightSubmissionPeriod;
  }

  public Duration getFlightCompletionPeriod() {
    return flightCompletionPeriod;
  }

  public int getFlightCompletionLimit() {
    return flightCompletionLimit;
  }

  public Duration getFatalFlightCompletionPeriod() {
    return fatalFlightCompletionPeriod;
  }

  public int getFatalFlightCompletionLimit() {
    return fatalFlightCompletionLimit;
  }

  public Duration getRecordResourceCountPeriod() {
    return recordResourceCountPeriod;
  }

  public void setSchedulerEnabled(boolean schedulerEnabled) {
    this.schedulerEnabled = schedulerEnabled;
  }

  public void setFlightSubmissionPeriod(Duration flightSubmissionPeriod) {
    this.flightSubmissionPeriod = flightSubmissionPeriod;
  }

  public void setFlightCompletionPeriod(Duration flightCompletionPeriod) {
    this.flightCompletionPeriod = flightCompletionPeriod;
  }

  public void setUnsubmittedFlightRecoveryLimit(int unsubmittedFlightRecoveryLimit) {
    this.unsubmittedFlightRecoveryLimit = unsubmittedFlightRecoveryLimit;
  }

  public void setFlightCompletionLimit(int flightCompletionLimit) {
    this.flightCompletionLimit = flightCompletionLimit;
  }

  public void setFatalFlightCompletionLimit(int fatalFlightCompletionLimit) {
    this.fatalFlightCompletionLimit = fatalFlightCompletionLimit;
  }

  public void setFatalFlightCompletionPeriod(Duration fatalFlightCompletionPeriod) {
    this.fatalFlightCompletionPeriod = fatalFlightCompletionPeriod;
  }

  public void setRecordResourceCountPeriod(Duration recordResourceCountPeriod) {
    this.recordResourceCountPeriod = recordResourceCountPeriod;
  }
}
