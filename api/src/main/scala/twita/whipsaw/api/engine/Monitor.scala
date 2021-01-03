package twita.whipsaw.api.engine

import java.time.Instant

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.PoisonPill
import akka.actor.Props
import akka.pattern.pipe
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class Probe(val monitor: Monitor, val workload: RegisteredWorkload)(implicit actorSystem: ActorSystem) {
  implicit val executionContext = actorSystem.dispatcher
  private var _timer: Option[Cancellable] = None

  monitor.director.managers.lookup(workload.id) match {
    case Some(manager) => activate(manager)
    case None => deactivate()
  }

  def report(workloadStatistics: WorkloadStatistics) = {
    println(s"reporting ${workloadStatistics}")
    monitor.monitorActor ! MonitorActor.WorkloadReport(workload.id, workloadStatistics)
  }

  def activate(manager: Manager) = {
    _timer.map(_.cancel())
    _timer = None
    manager.addProbe(this)
  }

  def deactivate() =
    _timer = Some(actorSystem.scheduler.scheduleAtFixedRate(5.seconds, 5.seconds)(new Refresh()))

  class Refresh extends Runnable {
    override def run = Await.result(refresh, 500.seconds)

    private def refresh(implicit executionContext: ExecutionContext): Future[Unit] = {
      monitor.director.managers.lookup(workload.id) match {
        case Some(manager) =>
          println("Found a manager!  Activating")
          activate(manager)
          Future.unit
        case None =>
          println("No manager!  Reporting")
          workload.refresh.map(rw => report(rw.stats))
      }
    }
  }
}

sealed trait WorkloadFilter

case class ForWorkload(workloadId: WorkloadId) extends WorkloadFilter

class Monitor(val director: Director, observer: ActorRef)(implicit actorSystem: ActorSystem) {
  def addFilter(filter: WorkloadFilter)(implicit executionContext: ExecutionContext) = filter match {
    case ForWorkload(workloadId) =>
      director.registeredWorkloads.get(byId(workloadId)).map {
        case Some(rw) => new Probe(this, rw)
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
