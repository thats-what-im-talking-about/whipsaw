package twita.whipsaw.api.engine

import akka.stream.Materializer
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Director {
  implicit def executionContext: ExecutionContext

  def registeredWorkloads: RegisteredWorkloads
  def registry: WorkloadRegistry

  /**
    * @return Managers that are currently working on a Workload at the request of this Director.
    */
  def managers: Managers

  def delegateRunnableWorkloads()(implicit m: Materializer): Future[Seq[(WorkloadId, (SchedulingStatus, ProcessingStatus))]] = {
    // TODO: Future.traverse won't work at large scale.  Come back through and Akka Stream this later.
    for {
      listToRun <- registeredWorkloads.getRunnable
      runnables <- Future.traverse(listToRun) { rw => registry(rw)}
      managerSeq <- Future.traverse(runnables) { runnable => managers.forWorkload(runnable) }
      processed <- Future.traverse(managerSeq) { manager => manager.executeWorkload().map(manager.workload.id -> _) }
    } yield processed
  }
}