package twita.whipsaw.monitor

import java.time.Instant

import play.api.libs.json.Json

case class WorkloadStatistics(scheduled: Long = 0,
                              running: Long = 0,
                              scheduledForRetry: Long = 0,
                              completed: Long = 0,
                              error: Long = 0,
                              runAt: Option[Instant] = Some(Instant.now)) {
  def apply(that: WorkloadStatistics): WorkloadStatistics = {
    copy(
      scheduled = this.scheduled + that.scheduled,
      running = this.running + that.running,
      scheduledForRetry = this.scheduledForRetry + that.scheduledForRetry,
      completed = this.completed + that.completed,
      error = this.error + that.error,
      runAt = that.runAt
    )
  }
}
object WorkloadStatistics {
  implicit val fmt = Json.format[WorkloadStatistics]
  val ScheduledToRunning = WorkloadStatistics(scheduled = -1, running = 1)
  val ScheduledRetryToRunning =
    WorkloadStatistics(scheduledForRetry = -1, running = 1)
  val RunningToError = WorkloadStatistics(running = -1, error = 1)
  val RunningToCompleted = WorkloadStatistics(running = -1, completed = 1)
  val RunningToScheduledRetry =
    WorkloadStatistics(running = -1, scheduledForRetry = 1)
  val RunningToScheduled = WorkloadStatistics(running = -1, scheduled = 1)
}
