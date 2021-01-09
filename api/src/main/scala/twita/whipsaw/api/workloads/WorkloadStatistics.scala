package twita.whipsaw.api.workloads

import java.time.Instant

import play.api.libs.json.Json

case class WorkloadStatistics(
    scheduled: Long = 0
  , running: Long = 0
  , scheduledForRetry: Long = 0
  , completed: Long = 0
  , error: Long = 0
  , runAt: Option[Instant] = Some(Instant.now)
) {
  def apply(that: WorkloadStatistics): WorkloadStatistics = {
    copy(
      scheduled = scheduled + that.scheduled
      , running = running + that.running
      , scheduledForRetry = scheduledForRetry + that.scheduledForRetry
      , completed = completed + that.completed
      , error = error + that.error
      , runAt = that.runAt
    )
  }
}
object WorkloadStatistics { implicit val fmt = Json.format[WorkloadStatistics] }
