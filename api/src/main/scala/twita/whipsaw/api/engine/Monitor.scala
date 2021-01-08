package twita.whipsaw.api.engine

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.api.workloads.WorkloadStatistics

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

sealed trait WorkloadFilter

case class ForWorkload(workloadId: WorkloadId) extends WorkloadFilter

class Monitor(val director: Director, observer: ActorRef)(implicit actorSystem: ActorSystem) {
  def addFilter(filter: WorkloadFilter)(implicit executionContext: ExecutionContext) = filter match {
    case ForWorkload(workloadId) =>
      director.registeredWorkloads.get(byId(workloadId)).map {
        case Some(rw) => new Probe(this, director.registeredWorkloads, rw)
        case None => throw new RuntimeException(s"workload ${workloadId} not found.")
      }
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
