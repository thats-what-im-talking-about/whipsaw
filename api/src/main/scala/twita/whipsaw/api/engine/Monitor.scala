package twita.whipsaw.api.engine

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.Future
import scala.concurrent.duration._

class Probe(director: Director, workloadId: WorkloadId)(implicit actorSystem: ActorSystem) {
  implicit val executionContext = actorSystem.dispatcher

  private var _monitorActor: ActorRef = _
  def connect(monitorActor: ActorRef) = {
    _monitorActor = monitorActor
    director.managers.lookup(workloadId) match {
      case Some(manager) => activate(manager)
      case None => deactivate()
    }
  }
  def report(workloadStatistics: WorkloadStatistics) = _monitorActor ! MonitorActor.WorkloadReport(workloadId, workloadStatistics)

  def activate(manager: Manager) = manager.addProbe(this)
  def deactivate() = actorSystem.actorOf(Props(new DeactivatedWorkloadActor), s"deactivated-workload-${workloadId.value}")

  object DeactivatedWorkloadActor {
    case object Check
  }
  class DeactivatedWorkloadActor extends Actor {
    override def preStart() = {
      context.system.scheduler.scheduleAtFixedRate(100.millis, 10.seconds, self, DeactivatedWorkloadActor.Check)
      for {
        rw <- director.registeredWorkloads.get(byId(workloadId))
        w <- rw match {
          case Some(registeredWorkload) => director.registry(registeredWorkload)
          case _ => Future.failed(new RuntimeException("workload not found"))
        }
        stats <- w.stats
      } yield _monitorActor ! MonitorActor.WorkloadReport(workloadId, stats)
    }

    override def receive: Receive = {
      case DeactivatedWorkloadActor.Check => director.managers.lookup(workloadId) match {
        case Some(manager) =>
          activate(manager)
          self ! PoisonPill
        case None =>
      }
    }
  }
}

sealed trait WorkloadFilter

case class ForWorkload(workloadId: WorkloadId) extends WorkloadFilter

class Monitor(director: Director, observer: ActorRef)(implicit actorSystem: ActorSystem) {
  def addFilter(filter: WorkloadFilter) = filter match {
    case ForWorkload(workloadId) =>
      val probe = new Probe(director, workloadId)
      probe.connect(monitorActor)
  }

  lazy val monitorActor = actorSystem.actorOf(Props(new MonitorActor(observer)), s"monitor-actor")
}

object MonitorActor {
  case class WorkloadReport(id: WorkloadId, stats: WorkloadStatistics)
  case object Emit
}

class MonitorActor(observer: ActorRef) extends Actor {
  implicit val executionContext = context.dispatcher

  override def receive: Receive = withData()

  def withData(
      data: Map[WorkloadId, WorkloadStatistics] = Map.empty
    , setToReport: Set[WorkloadId] = Set.empty
    , timer: Option[Cancellable] = None
  ): Receive = {
    case MonitorActor.WorkloadReport(id, stats) if timer.isEmpty =>
      val newTimer = context.system.scheduler.scheduleOnce(1.second) {
        self ! MonitorActor.Emit
      }
      context.become(withData(data + (id -> stats), setToReport + id, Some(newTimer)))
    case MonitorActor.WorkloadReport(id, stats) =>
      context.become(withData(data + (id -> stats), setToReport + id, timer))
    case MonitorActor.Emit =>
      observer ! setToReport.map(id => id -> data(id)).toMap
      context.become(withData(data, Set.empty, None))
  }
}
