package twita.whipsaw.api.engine

import akka.actor.ActorSystem
import akka.actor.Cancellable
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.workloads.WorkloadStatistics

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class Probe(val monitor: Monitor, workloads: RegisteredWorkloads, val workload: RegisteredWorkload)(implicit actorSystem: ActorSystem) {
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
    override def run = Await.result(refresh, 5.seconds)

    private def refresh(implicit executionContext: ExecutionContext): Future[Unit] = {
      monitor.director.managers.lookup(workload.id) match {
        case Some(manager) =>
          println("Found a manager!  Activating")
          activate(manager)
          Future.unit
        case None =>
          println("No manager!  Reporting")
          workloads.get(byId(workload.id)).map {
            case Some(refreshedWorkload) =>
              println("refreshed stats: ${refreshedWorkload.stats}")
              refreshedWorkload.stats
            case None => ???
          }
      }
    }
  }
}

