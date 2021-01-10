package twita.whipsaw.monitor

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Cancellable
import akka.actor.Props
import akka.pattern.pipe
import play.api.libs.json.Json
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.engine.Director
import twita.whipsaw.api.registry.RegisteredWorkload
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.duration._

object MonitorActor {
  def props(director: Director, out: ActorRef) = Props(new MonitorActor(director, out))

  case class WorkloadReport(id: WorkloadId, stats: WorkloadStatistics)
  case object Emit
  case class AddObserver(observer: ActorRef)

  val forWorkloadId = """addWorkloadId:(.*)""".r
}

class MonitorActor(director: Director, out: ActorRef) extends Actor with ActorLogging {
  implicit val executionContext = context.dispatcher

  override def receive: Receive = activeState()

  def activeState(
      data: Map[WorkloadId, WorkloadStatistics] = Map.empty
    , setToReport: Set[WorkloadId] = Set.empty
    , timer: Option[Cancellable] = None
  ): Receive = {

    case MonitorActor.WorkloadReport(id, stats) if timer.isEmpty =>
      val newTimer = context.system.scheduler.scheduleOnce(1.second) {
        self ! MonitorActor.Emit
      }
      context.become(activeState(data + (id -> stats), setToReport + id, Some(newTimer)))

    case MonitorActor.WorkloadReport(id, stats) =>
      context.become(activeState(data + (id -> stats), setToReport + id, timer))

    case MonitorActor.Emit =>
      val report = setToReport.map(id => id.value -> data(id)).toMap
      out ! Json.toJson(report).toString
      context.become(activeState(data, Set.empty, None))

    case Some(workload: RegisteredWorkload) => context.actorOf(Props(new Probe(workload, director)))

    case str: String => str match {
      case MonitorActor.forWorkloadId(id) =>
        pipe(director.registeredWorkloads.get(byId(WorkloadId(id)))).to(self)
      case message => log.warning("Unknown message {}", message)
    }
  }
}
