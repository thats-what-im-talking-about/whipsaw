package twita.whipsaw.api.engine

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Cancellable
import akka.pattern.pipe
import twita.whipsaw.api.engine.WorkloadStatsTracker.AddProbe
import twita.whipsaw.api.workloads.WorkloadStatistics

import scala.concurrent.duration._

class Probe(workload: RegisteredWorkload, director: Director) extends Actor with ActorLogging {
  implicit def executionContext = context.system.dispatcher

  def timer = context.system.scheduler.scheduleAtFixedRate(5.seconds, 5.seconds, self, Probe.CheckActive)

  override def receive: Receive = deactivated(timer)

  override def preStart(): Unit = {
    self ! Probe.CheckActive
  }

  override def postStop(): Unit = log.warning(s"stopped Probe of workload id ${workload.id}")

  def activated(): Receive = {
    case stats: WorkloadStatistics => context.parent ! MonitorActor.WorkloadReport(workload.id, stats)
    case manager: Manager => manager.statsTracker ! AddProbe(workload.id, self)
    case Probe.Deactivate =>
      context.become(deactivated(timer))
  }

  def deactivated(timer: Cancellable, stats: Option[WorkloadStatistics] = None): Receive = {
    case Probe.CheckActive => director.managers.lookup(workload.id) match {
      case Some(manager) =>
        self ! manager
        timer.cancel()
        context.become(activated())
      case None =>
        pipe(workload.refresh()).to(self)
    }
    case refreshedWorkload: RegisteredWorkload =>
      if(!stats.exists(_ == refreshedWorkload.stats))
        context.parent ! MonitorActor.WorkloadReport(refreshedWorkload.id, refreshedWorkload.stats)
      context.become(deactivated(timer, Some(workload.stats)))
  }
}

object Probe {
  case object Deactivate
  case object CheckActive
}
