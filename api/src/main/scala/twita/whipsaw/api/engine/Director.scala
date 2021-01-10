package twita.whipsaw.api.engine

import akka.stream.Materializer
import twita.whipsaw.api.registry.RegisteredWorkloads
import twita.whipsaw.api.registry.WorkloadRegistry
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * The Director sits on top of the whole workload hierarchy and knows how to start up Workloads that are due to be
  * started.
  */
trait Director {
  implicit def executionContext: ExecutionContext

  /**
    * @return a repository of all of the workloads that currently exist in the system.
    */
  def registeredWorkloads: RegisteredWorkloads

  /**
    * @return A WorkloadRegistry instance that this whole Workload Management system will be able to use to create
    *         new {{Workload}} instances, either by {{id}}, or by the application-defined {{Metadata}} for that
    *         {{Workload}}
    */
  def registry: WorkloadRegistry

  /**
    * @return Managers that this {{Director]} has delegated a {{Workload}} to.  This is an interface that may be
    *         queried to find whether there is a manager currently running a given {{Workload}}, and it also contains
    *         methods for creating new {{Manager}} instances.
    */
  def managers: Managers

  /**
    * Method that is invoked in order to pull the {{Workload}}s that are due to run, and to delegate that work out to
    * a {{Manager}} that is instantiated just for that {{Workload}}.
    *
    * @param m Akka Streams materializer instance that will be needed further down in the call stack to do the work
    *          of actually processing the Workload.
    * @return List or workloads that were actually run.
    */
  def delegateRunnableWorkloads()(implicit m: Materializer): Future[List[(WorkloadId, (SchedulingStatus, ProcessingStatus))]] = {
    // TODO: Future.traverse won't work at large scale.  Come back through and Akka Stream this later.
    for {
      listToRun <- registeredWorkloads.getRunnable
      runnables <- Future.traverse(listToRun) { rw => registry(rw)}
      managerSeq <- Future.traverse(runnables) { runnable => managers.forWorkload(runnable) }
      processed <- Future.traverse(managerSeq) { manager =>
        managers.activate(manager).map(o => o.workload.id -> (o.workload.schedulingStatus, o.workload.processingStatus))
      }
    } yield processed
  }
}