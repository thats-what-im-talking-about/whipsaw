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
import twita.whipsaw.monitor.WorkloadStatsTracker.SetWorkerFactory

import scala.concurrent.duration._

class WorkloadStatsTracker(manager: Manager) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  override def receive: Receive =
    activated(WorkloadStatistics(), Map.empty, None, None)

  val wl = manager.workload

  def activated(workloadStatistics: WorkloadStatistics,
                probes: Map[WorkloadId, ActorRef],
                workerFactory: Option[ActorRef],
                timer: Option[Cancellable]): Receive = {
    // Update stats, start a timer if one is not set.
    case updateStats: WorkloadStatistics if timer.isEmpty =>
      val timer = context.system.scheduler.scheduleOnce(1.second) {
        self ! WorkloadStatsTracker.Report
        self ! WorkloadStatsTracker.SaveStats
      }
      context.become(
        activated(
          workloadStatistics(updateStats),
          probes,
          workerFactory,
          Some(timer)
        )
      )

    // Update stats only
    case updateStats: WorkloadStatistics =>
      context.become(
        activated(workloadStatistics(updateStats), probes, workerFactory, timer)
      )

    case itemsPerSec: Double =>
      context.become(
        activated(
          workloadStatistics.copy(itemsPerSec = Some(itemsPerSec)),
          probes,
          workerFactory,
          timer
        )
      )

    // Report the current WorkloadStats to all subscribing probes
    case WorkloadStatsTracker.Report =>
      probes.foreach { case (_, probe) => probe ! workloadStatistics }
      context.become(activated(workloadStatistics, probes, workerFactory, None))

    // Persist the WorkloadStats object into the workload
    case WorkloadStatsTracker.SaveStats =>
      println(s"saving stats for workload ${workloadStatistics}")
      wl(wl.StatsUpdated(workloadStatistics))

    case WorkloadStatsTracker.AddProbe(workloadId, probe) =>
      context.become(
        activated(
          workloadStatistics,
          probes + (workloadId -> probe),
          workerFactory,
          timer
        )
      )

    case SetWorkerFactory(workerFactory) =>
      log.info("Received a worker factory")
      self ! WorkerFactoryActor.SetWorkerPoolSize(
        workloadStatistics.maxWorkers.getOrElse(0)
      )
      context.become(
        activated(workloadStatistics, probes, Some(workerFactory), timer)
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
          workerFactory,
          timer
        )
      )

    case WorkloadStatsTracker.Deactivate(nextRunAt) =>
      self ! WorkloadStatsTracker.SaveStats
      probes.foreach { case (_, probe) => probe ! Probe.Deactivate }
      context.become(
        deactivating(
          workloadStatistics(WorkloadStatistics(runAt = nextRunAt)),
          timer
        )
      )
  }

  def deactivating(workloadStatistics: WorkloadStatistics,
                   timer: Option[Cancellable] = None): Receive = {
    case WorkloadStatsTracker.SaveStats =>
      pipe(wl(wl.StatsUpdated(workloadStatistics))).to(self)
    case _: Workload[_, _, _] =>
      timer.foreach(_.cancel())
      context.stop(self)
  }
}

object WorkloadStatsTracker {
  case object SaveStats
  case class AddProbe(workloadId: WorkloadId, probe: ActorRef)
  case class SetWorkerFactory(workerFactory: ActorRef)
  case class RemoveProbe(workloadId: WorkloadId)
  case class Deactivate(nextRunAt: Option[Instant])
  case object Report
}
