package twita.whipsaw.monitor

import java.time.Instant
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.pattern.pipe
import twita.whipsaw.api.engine.Manager
import twita.whipsaw.api.engine.WorkerFactoryActor
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.monitor.WorkloadStatsTracker.SaveStats
import twita.whipsaw.monitor.WorkloadStatsTracker.StatsSaved
import twita.whipsaw.monitor.WorkloadStatsTracker.SetWorkerFactory
import twita.whipsaw.monitor.WorkloadStatsTracker.TakeMeasurement

import scala.concurrent.duration._

case class WorkloadStatsState(
  workloadStatistics: WorkloadStatistics = WorkloadStatistics(),
  probes: Map[WorkloadId, ActorRef] = Map.empty,
  count: Int = 0,
  workerFactory: Option[ActorRef] = None,
  saveTimer: Option[Cancellable] = None,
  taskTimer: Option[Cancellable] = None
)

class WorkloadStatsTracker(manager: Manager) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  override def preStart(): Unit = {
    log.info(s"Starting up stats tracker for ${manager.workload.id}")
    context.system.scheduler.scheduleOnce(1.second, self, TakeMeasurement())
  }

  override def receive: Receive = activated(WorkloadStatsState())

  val wl = manager.workload

  def activated(state: WorkloadStatsState): Receive = {

    // Sent whenever there is a stats update.  This handler jus does an in memory update of the workload stats
    // and schedules those stats to be saved in 1 second (if the scheduling has not already happened).
    case updateStats: WorkloadStatistics =>
      val newSaveTimer = state.saveTimer.fold(
        context.system.scheduler.scheduleOnce(1.second, self, SaveStats)
      )(t => t)
      context.become(
        activated(state.copy(
          workloadStatistics = state.workloadStatistics(updateStats),
          count = state.count + Math.max(0, updateStats.running.toInt),
          saveTimer = Some(newSaveTimer),
        ))
      )

    // Measurements are taken periodically and saved into the WorkloadStatistics object.  We count the number
    // of items that have been processed divided by the number of milliseconds since the beginning of the
    // monitoring period.  Then we reset the timer and reset the counter back to zero.
    case TakeMeasurement(startTime) =>
      val itemsPerSec = state.count * 1000.0 / (System.currentTimeMillis() - startTime)
      val newTaskTimer = context.system.scheduler.scheduleOnce(1.second, self, TakeMeasurement())
      context.become(activated(state.copy(
        workloadStatistics = state.workloadStatistics.copy(itemsPerSec = Some(itemsPerSec)),
        count = 0,
        taskTimer = Some(newTaskTimer)
      )))

    // Persist the WorkloadStats object into the workload
    case WorkloadStatsTracker.SaveStats =>
      log.info(s"saving stats for workload ${state.workloadStatistics}")
      state.probes.foreach { case (_, probe) => probe ! state.workloadStatistics }
      wl(wl.StatsUpdated(state.workloadStatistics)).map(_ => StatsSaved).to(self)

    // Upon completion of saving the stats, we clean up that timer as well as the state of the stats tracker.
    case StatsSaved =>
      state.saveTimer.foreach(_.cancel())
      context.become(activated(state.copy(saveTimer = None)))

    // Probes use this message to announce to the workload that they are interested in getting the stats updates.
    case WorkloadStatsTracker.AddProbe(workloadId, probe) =>
      context.become(activated(state.copy(probes = state.probes + (workloadId -> probe))))

    // Adds the workerFactory to this stats tracker instance.  The worker factory is the actor that lives in
    // the Workload execution graph and is responsible for managing the worker slots that actually do the
    // work.  We need the worker factory here so that there we have an actor to send `maxWorkers` settings to
    // for this Workload.
    case SetWorkerFactory(workerFactory) =>
      log.info("Received a worker factory")
      self ! WorkerFactoryActor.SetWorkerPoolSize(state.workloadStatistics.maxWorkers.getOrElse(0))
      context.become(activated(state.copy(workerFactory = Some(workerFactory))))

    // Sets the pool size on the worker factory and saves it in the state.
    case setPoolSize: WorkerFactoryActor.SetWorkerPoolSize =>
      log.info(s"setting the pool size to ${setPoolSize.to}, have a worker factory? ${state.workerFactory.isDefined}")
      state.workerFactory.foreach(_ ! setPoolSize)
      self ! WorkloadStatsTracker.SaveStats
      context.become(activated(state.copy(
        workloadStatistics = state.workloadStatistics.copy(maxWorkers = Some(setPoolSize.to))
      )))

    case WorkloadStatsTracker.Deactivate(nextRunAt) =>
      self ! WorkloadStatsTracker.SaveStats
      state.probes.foreach { case (_, probe) => probe ! Probe.Deactivate }
      state.saveTimer.foreach(_.cancel())
      state.taskTimer.foreach(_.cancel())
      context.become(
        deactivating(state.workloadStatistics(WorkloadStatistics(runAt = nextRunAt)))
      )
  }

  def deactivating(workloadStatistics: WorkloadStatistics): Receive = {
    case WorkloadStatsTracker.SaveStats =>
      pipe(wl(wl.StatsUpdated(workloadStatistics))).to(self)
    case _: Workload[_, _, _] =>
      context.stop(self)
  }
}

object WorkloadStatsTracker {
  case object SaveStats
  case object StatsSaved
  case class AddProbe(workloadId: WorkloadId, probe: ActorRef)
  case class SetWorkerFactory(workerFactory: ActorRef)
  case class RemoveProbe(workloadId: WorkloadId)
  case class Deactivate(nextRunAt: Option[Instant])
  case class TakeMeasurement(startTime: Long = System.currentTimeMillis())
}
