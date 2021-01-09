package twita.whipsaw.api.engine

import java.time.Instant

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.pattern.pipe
import akka.stream.Materializer
import twita.whipsaw.api.engine.WorkloadStatsTracker.SaveStats
import twita.whipsaw.api.workloads.ItemResult
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.api.workloads.WorkloadStatistics

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait WorkloadEvent

class WorkloadStatsTracker(manager: Manager) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  override def receive: Receive = activated(WorkloadStatistics(), Map.empty, None)

  def activated(
      workloadStatistics: WorkloadStatistics
    , probes: Map[WorkloadId, ActorRef]
    , timer: Option[Cancellable]
  ): Receive = {
    // Update stats, start a timer if one is not set.
    case updateStats: WorkloadStatistics if timer.isEmpty =>
      val timer = probes.headOption.map { _ =>
        context.system.scheduler.scheduleOnce(1.second) {
          self ! WorkloadStatsTracker.Report
        }
      }
      context.become(activated(workloadStatistics(updateStats), probes, timer))

    // Update stats only
    case updateStats: WorkloadStatistics =>
      context.become(activated(workloadStatistics(updateStats), probes, timer))

    // Report the current WorkloadStats to all subscribing probes
    case WorkloadStatsTracker.Report =>
      probes.foreach { case (workloadId, probe) => probe ! workloadStatistics }
      context.become(activated(workloadStatistics, probes, None))

    // Persist the WorkloadStats object into the workload
    case WorkloadStatsTracker.SaveStats => manager.workload.stats = workloadStatistics

    case WorkloadStatsTracker.AddProbe(workloadId, probe) =>
      context.become(activated(workloadStatistics, probes + (workloadId -> probe), timer))

    case WorkloadStatsTracker.Deactivate(nextRunAt) =>
      self ! WorkloadStatsTracker.SaveStats
      probes.foreach { case (_, probe) => probe ! Probe.Deactivate }
      context.become(deactivating(workloadStatistics(WorkloadStatistics(runAt = nextRunAt)), timer))
  }

  def deactivating(workloadStatistics: WorkloadStatistics, timer: Option[Cancellable] = None): Receive = {
    case WorkloadStatsTracker.SaveStats => pipe(manager.workload.stats = workloadStatistics).to(self)
    case workloadStats =>
      timer.map(_.cancel())
      self ! PoisonPill
  }
}

object WorkloadStatsTracker {
  case object SaveStats
  case class AddProbe(workloadId: WorkloadId, probe: ActorRef)
  case class RemoveProbe(workloadId: WorkloadId)
  case class Deactivate(nextRunAt: Option[Instant])
  case object Report
}

trait Manager {
  implicit def executionContext: ExecutionContext
  def workload: Workload[_, _, _]
  def workers: Workers
  def actorSystem: ActorSystem
  def director: Director

  lazy val statsTracker = actorSystem.actorOf(Props(new WorkloadStatsTracker(this)), s"stats-tracker-${workload.id.value}")

  def executeWorkload()(implicit m: Materializer): Future[(SchedulingStatus, ProcessingStatus)] = {
    director.managers.activate(this)

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
          nextRunAt <- wl.workItems.nextRunAt
          status = nextRunAt match {
              case Some(_) => ProcessingStatus.Waiting
              case _ => ProcessingStatus.Completed
            }
          _ <- wl(wl.ProcessingStatusUpdated(status))
        } yield result).flatMap { numProcessed =>
          statsTracker ! WorkloadStatsTracker.SaveStats
          Thread.sleep(500)
          numProcessed match {
            case 0 if last =>
              workload.workItems.nextRunAt.map { nextRunAt =>
                statsTracker ! WorkloadStatsTracker.Deactivate(nextRunAt)
                wl.processingStatus
              }
            case 0 => processRunnablesFt(true)
            case n => processRunnablesFt(false)
          }
        }
    }

    val processResult = processRunnablesFt(false)

    for {
      stats <- wl.stats
      _ = statsTracker ! stats.copy(runAt = None)
      _ = statsTracker ! SaveStats
      sStatus <- scheduledItemsFt
      pStatus <- processResult
    } yield {
      director.managers.deactivate(this)
      (sStatus, pStatus)
    }
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

  def lookup(workloadId: WorkloadId): Option[Manager]

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
