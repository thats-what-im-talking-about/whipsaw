package twita.whipsaw.monitor

import java.time.Instant

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.pattern.pipe
import twita.whipsaw.api.engine.Manager
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.duration._

class WorkloadStatsTracker[Attr](manager: Manager[Attr])
    extends Actor
    with ActorLogging {
  implicit val executionContext = context.dispatcher

  override def receive: Receive =
    activated(WorkloadStatistics(), Map.empty, None)

  val wl = manager.workload

  def activated(workloadStatistics: WorkloadStatistics,
                probes: Map[WorkloadId, ActorRef],
                timer: Option[Cancellable]): Receive = {
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
    case WorkloadStatsTracker.SaveStats =>
      wl(wl.StatsUpdated(workloadStatistics))

    case WorkloadStatsTracker.AddProbe(workloadId, probe) =>
      context.become(
        activated(workloadStatistics, probes + (workloadId -> probe), timer)
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
    case workload: Workload[_, _, _] =>
      timer.foreach(_.cancel())
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
