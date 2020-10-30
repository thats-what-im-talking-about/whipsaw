package twita.whipsaw.api.engine

import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait Director {
  implicit def executionContext: ExecutionContext

  /**
    * @return Workloads instance representing all of the Workloads that this Director is responsible for.
    */
  def managedWorkloads: ManagedWorkloads

  /**
    * @return Managers that are currently working on a Workload at the request of this Director.
    */
  def managers: Managers

  def delegateRunnableWorkloads(): Future[Seq[(WorkloadId, (SchedulingStatus, ProcessingStatus))]] = {
    for {
      runnables <- managedWorkloads.getRunnable()
      managerSeq <- Future.traverse(runnables) { runnable => managers.forWorkload(runnable) }
      processed <- Future.traverse(managerSeq) { manager => manager.executeWorkload().map(manager.workload.id -> _) }
    } yield processed
  }
}
