package twita.whipsaw.api.engine

import akka.stream.Materializer
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * The Director sits on top of the whole workload hierarchy and it is this object's job to wake up from time to
  * time to see if there are Workloads that need to be activated.
  */
trait Director {
  implicit def executionContext: ExecutionContext

  /**
    * @return a repository of all of the workloads that currently exist in the system.  Note that a {{RegisteredWorkload}}
    *         is not the same thing as a normnal {{Workload}} object, in that it does not carry along the generic
    *         information that {{Workload}}s have.  The {{Director}} doesn't actually care about the specific work that
    *         a {{Workload}} has been created to do.  It just needs to be able to figure out that a {{Workload}} is
    *         due for processing and that a {{Manager}} needs to be created to take care of doing that work.
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
  def delegateRunnableWorkloads()(implicit m: Materializer): Future[Unit] = {
    // TODO: Future.traverse won't work at large scale.  Come back through and Akka Stream this later.
    for {
      listToRun <- registeredWorkloads.getRunnable
      runnables <- Future.traverse(listToRun) { rw => registry(rw)}
      managerSeq <- Future.traverse(runnables) { runnable => managers.forWorkload(runnable) }
      processed = managerSeq.map(managers.activate)
    } yield processed
  }

  def onManagerComplete(manager: Manager): Future[Unit] = managers.deactivate(manager)
}