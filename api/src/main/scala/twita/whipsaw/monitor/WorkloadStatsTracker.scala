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

class WorkloadStatsTracker(manager: Manager) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  override def preStart(): Unit =
    log.info(s"Starting up stats tracker for ${manager.workload.id}")
  context.system.scheduler.scheduleOnce(1.second, self, TakeMeasurement())

  override def receive: Receive =
    activated(WorkloadStatistics(), Map.empty, 0, None, None, None)

  val wl = manager.workload

  def activated(workloadStatistics: WorkloadStatistics,
                probes: Map[WorkloadId, ActorRef],
                count: Int,
                workerFactory: Option[ActorRef],
                saveTimer: Option[Cancellable],
                taskTimer: Option[Cancellable]): Receive = {

    // Update stats, start a timer if one is not set.
    case updateStats: WorkloadStatistics =>
      val newSaveTimer = saveTimer.fold(
        context.system.scheduler.scheduleOnce(1.second, self, SaveStats)
      )(t => t)
      context.become(
        activated(
          workloadStatistics(updateStats),
          probes,
          count + Math.max(0, updateStats.running.toInt),
          workerFactory,
          Some(newSaveTimer),
          taskTimer
        )
      )

    case TakeMeasurement(startTime) =>
      val itemsPerSec =
        count * 1000.0 / (System.currentTimeMillis() - startTime)
      val newTaskTimer =
        context.system.scheduler.scheduleOnce(1.second, self, TakeMeasurement())
      context.become(
        activated(
          workloadStatistics.copy(itemsPerSec = Some(itemsPerSec)),
          probes,
          0,
          workerFactory,
          saveTimer,
          Some(newTaskTimer)
        )
      )

    // Persist the WorkloadStats object into the workload
    case WorkloadStatsTracker.SaveStats =>
      log.info(s"saving stats for workload ${workloadStatistics}")
      probes.foreach { case (_, probe) => probe ! workloadStatistics }
      wl(wl.StatsUpdated(workloadStatistics)).map(_ => StatsSaved).to(self)

    case StatsSaved =>
      saveTimer.foreach(_.cancel())
      context.become(
        activated(
          workloadStatistics,
          probes,
          count,
          workerFactory,
          None,
          taskTimer
        )
      )

    case WorkloadStatsTracker.AddProbe(workloadId, probe) =>
      context.become(
        activated(
          workloadStatistics,
          probes + (workloadId -> probe),
          count,
          workerFactory,
          saveTimer,
          taskTimer
        )
      )

    case SetWorkerFactory(workerFactory) =>
      log.info("Received a worker factory")
      self ! WorkerFactoryActor.SetWorkerPoolSize(
        workloadStatistics.maxWorkers.getOrElse(0)
      )
      context.become(
        activated(
          workloadStatistics,
          probes,
          count,
          Some(workerFactory),
          saveTimer,
          taskTimer
        )
      )

    case setPoolSize: WorkerFactoryActor.SetWorkerPoolSize =>
      log.info(
        s"setting the pool size to ${setPoolSize.to}, have a worker factory? ${workerFactory.isDefined}"
      )
      workerFactory.foreach(_ ! setPoolSize)
      self ! WorkloadStatsTracker.SaveStats
      context.become(
        activated(
          workloadStatistics.copy(maxWorkers = Some(setPoolSize.to)),
          probes,
          count,
          workerFactory,
          saveTimer,
          taskTimer
        )
      )

    case WorkloadStatsTracker.Deactivate(nextRunAt) =>
      self ! WorkloadStatsTracker.SaveStats
      probes.foreach { case (_, probe) => probe ! Probe.Deactivate }
      saveTimer.foreach(_.cancel())
      taskTimer.foreach(_.cancel())
      context.become(
        deactivating(workloadStatistics(WorkloadStatistics(runAt = nextRunAt)))
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
