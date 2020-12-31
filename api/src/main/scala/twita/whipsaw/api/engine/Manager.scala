package twita.whipsaw.api.engine

import java.time.Instant

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.stream.Materializer
import play.api.libs.json.Json
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait WorkloadEvent

case class WorkloadStatistics(
    scheduled: Long = 0
  , running: Long = 0
  , scheduledForRetry: Long = 0
  , completed: Long = 0
  , error: Long = 0
) {
  def apply(that: WorkloadStatistics): WorkloadStatistics = {
    copy(
        scheduled = scheduled + that.scheduled
      , running = running + that.running
      , scheduledForRetry = scheduledForRetry + that.scheduledForRetry
      , completed = completed + that.completed
      , error = error + that.error
    )
  }
}
object WorkloadStatistics { implicit val fmt = Json.format[WorkloadStatistics] }

class WorkloadStatsTracker extends Actor {
  override def receive: Receive = receiveStats(WorkloadStatistics())

  def receiveStats(workloadStatistics: WorkloadStatistics): Receive = {
    case updateStats: WorkloadStatistics => context.become(receiveStats(workloadStatistics(updateStats)))
    case WorkloadStatsTracker.SaveStats(workload) => workload.stats = workloadStatistics
  }
}
object WorkloadStatsTracker {
  case class SaveStats(workload: Workload[_,_,_])
}

trait Manager {
  implicit def executionContext: ExecutionContext
  def workload: Workload[_, _, _]
  def workers: Workers
  def actorSystem: ActorSystem
  def director: Director

  lazy val statsTracker = actorSystem.actorOf(Props[WorkloadStatsTracker])

  def executeWorkload()(implicit m: Materializer): Future[(SchedulingStatus, ProcessingStatus)] = {

    val wl = workload // transforms workload into a val, fixes compiler error

    val scheduledItemsFt = workload.schedulingStatus match {
      case SchedulingStatus.Completed => Future.successful(SchedulingStatus.Completed)
      case _ => for {
        running <- wl(wl.ScheduleStatusUpdated(SchedulingStatus.Running))
        result <- wl.schedule(statsTracker)
        updated <- wl(wl.ScheduleStatusUpdated(result))
      } yield result
    }

    def processRunnablesFt(last: Boolean): Future[ProcessingStatus] = workload.processingStatus match {
      case ProcessingStatus.Completed => Future.successful(ProcessingStatus.Completed)
      case _ =>
        (for {
          running <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Running))
          runnableItems <- wl.workItems.runnableItemSource(Instant.now, workload.batchSize)
          result <- runnableItems
            .mapAsyncUnordered(workload.desiredNumWorkers)(workers.forItem(_).flatMap { item =>
              item.workItem.retryCount match {
                case 0 => statsTracker ! WorkloadStatistics(scheduled = -1, running = 1)
                case _ => statsTracker ! WorkloadStatistics(scheduledForRetry = -1, running = 1)
              }
              item.process().map {
                case r: ItemResult.Error => statsTracker ! WorkloadStatistics(running = -1, error = 1)
                case ItemResult.Done => statsTracker ! WorkloadStatistics(running = -1, completed = 1)
                case r: ItemResult.Retry => statsTracker ! WorkloadStatistics(running = -1, scheduledForRetry = 1)
                case r: ItemResult.Reschedule => statsTracker ! WorkloadStatistics(running = -1, scheduled = 1)
              }
            })
            .runFold(0) { case (result, _) => result + 1 }
          _ <- wl(wl.ProcessingStatusUpdated(ProcessingStatus.Completed))
        } yield result).flatMap { numProcessed =>
          println(s"processed ${numProcessed} entries")
          Thread.sleep(5)
          statsTracker ! WorkloadStatsTracker.SaveStats(wl)
          numProcessed match {
            case 0 if last => Future.successful(ProcessingStatus.Completed)
            case 0 => processRunnablesFt(true)
            case n => processRunnablesFt(false)
          }
        }
    }

    val processResult = processRunnablesFt(false)

    for {
      stats <- wl.stats
      _ = statsTracker ! stats
      sStatus <- scheduledItemsFt
      pStatus <- processResult
    } yield (sStatus, pStatus)
  }
}

trait Managers {
  /**
    * @param workload Workload instance whose Manager we are looking for.
    * @return Manager instance (either new or previously created) for the workload provided.
    */
  def forWorkload(workload: Workload[_, _, _]): Future[Manager]

  /**
    * @param workloadId WorkloadId whose Manager we are looking for.
    * @return Manager instance (either new or previously created) for the workload provided.
    */
  def forWorkloadId(workloadId: WorkloadId): Future[Manager]

  /**
    * Adds a Manager instance to this Managers collection, and then "activates" the workload.
    * @param manager Manager of the workload to be activated
    */
  def activate(manager: Manager): Future[Unit]

  /**
    * Removes a Manager instance from this Managers collection, and then "deactivates" the workload.
    * @param manager Manager of the workload to be deactivated
    */
  def deactivate(manager: Manager): Future[Unit]
}
