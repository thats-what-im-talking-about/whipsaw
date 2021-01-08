package twita.whipsaw.api.engine

import akka.actor.ActorSystem
import akka.actor.Cancellable
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.api.workloads.WorkloadStatistics

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure

class Probe(val monitor: Monitor, workloads: RegisteredWorkloads, val workload: RegisteredWorkload)(implicit actorSystem: ActorSystem) {
  implicit val executionContext = actorSystem.dispatcher
  private var _timer: Option[Cancellable] = None

  deactivate()

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
    _timer = Some(actorSystem.scheduler.scheduleAtFixedRate(5.seconds, 5.seconds)(new Refresh(this, workload.id, workloads)))
}

class Refresh(probe: Probe, workloadId: WorkloadId, workloads: RegisteredWorkloads) extends Runnable {
  import scala.concurrent.ExecutionContext.Implicits.global
  override def run = {
    refresh().onComplete {
      case Success(t) => println(t)
      case Failure(t) => t.printStackTrace()
    }
  }

  def refresh(): Future[Unit] = {
    probe.monitor.director.managers.lookup(workloadId) match {
      case Some(manager) =>
        println("Found a manager!  Activating")
        probe.activate(manager)
        Future.unit
      case None =>
        println("No manager!  Reporting")
        println(probe.monitor.director.registeredWorkloads == workloads)
        workloads.get(byId(workloadId)).map {
          case Some(refreshedWorkload) =>
            println(s"refreshed stats: ${refreshedWorkload.stats}")
            refreshedWorkload.stats
          case None => ???
        }
    }
  }
}

