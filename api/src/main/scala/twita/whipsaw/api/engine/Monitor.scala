package twita.whipsaw.api.engine

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props
import play.api.libs.json.Json
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

  val monitorActor = actorSystem.actorOf(Props[MonitorActor], s"monitor-actor")
  monitorActor ! MonitorActor.AddObserver(observer)
}

object MonitorActor {
  case class WorkloadReport(id: WorkloadId, stats: WorkloadStatistics)
  case object Emit
  case class AddObserver(observer: ActorRef)
}

class MonitorActor extends Actor {
  implicit val executionContext = context.dispatcher

  override def receive: Receive = withData()

  override def preStart(): Unit = {
    println("Starting up a monitor...")
  }

  def withData(
      data: Map[WorkloadId, WorkloadStatistics] = Map.empty
    , observer: Option[ActorRef] = None
    , setToReport: Set[WorkloadId] = Set.empty
    , timer: Option[Cancellable] = None
  ): Receive = {

    case MonitorActor.WorkloadReport(id, stats) if timer.isEmpty =>
      val newTimer = context.system.scheduler.scheduleOnce(1.second) {
        self ! MonitorActor.Emit
      }
      context.become(withData(data + (id -> stats), observer, setToReport + id, Some(newTimer)))

    case MonitorActor.WorkloadReport(id, stats) =>
      context.become(withData(data + (id -> stats), observer, setToReport + id, timer))

    case MonitorActor.Emit =>
      val report = setToReport.map(id => id.value -> data(id)).toMap
      observer.map { o => o ! Json.toJson(report).toString }
      context.become(withData(data, observer, Set.empty, None))

    case MonitorActor.AddObserver(observer) =>
      context.become(withData(observer = Some(observer)))
  }
}
