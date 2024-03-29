package twita.whipsaw.api.engine

import akka.stream.Materializer
import twita.whipsaw.api.registry.RegisteredWorkload
import twita.whipsaw.api.registry.RegisteredWorkloads
import twita.whipsaw.api.registry.WorkloadRegistry

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * The Director sits on top of the whole workload hierarchy and knows how to start up `Workload`s that are due to be
  * started.  The `Director` is not actually itself a daemon but rather, it is an instance that encapsulates the
  * functionality a `Director` daemon would need to have in order to oversee a series of `Workload`s.
  * It is up to the application to place the `Director` instance into some context (e.g. an
  * Akka Actor) and to schedule that process to do the necessary managerial tasks defined by this trait from time
  * to time.  The Director isn't responsible for doing all of the work, but it is responsible for delegating the
  * work and monitoring whether it is getting done.
  */
trait Director {
  implicit def executionContext: ExecutionContext

  /**
    * @return a repository of all of the `RegisteredWorkload`s that currently exist in the system.
    */
  def registeredWorkloads: RegisteredWorkloads

  /**
    * @return A `WorkloadRegistry` instance that this whole Workload Management system will be able to use to create
    *         new `Workload` instances.
    */
  def registry: WorkloadRegistry

  /**
    * @return Managers that this `Director` has delegated a `Workload` to.  This is an interface that may be
    *         queried to find whether there is a `Manager` currently running a given `Workload`, and it also contains
    *         methods for creating new `Manager` instances.
    */
  def managers: Managers

  /**
    * Method that is invoked in order to pull the `Workload`s that are due to run, and to delegate that work out to
    * a `Manager` that is instantiated just for that `Workload`.
    *
    * @param m Akka Streams materializer instance that will be needed further down in the call stack to do the work
    *          of actually processing the Workload.
    * @return List of `RegisteredWorkload`s that were actually run.
    */
  def delegateRunnableWorkloads()(
    implicit m: Materializer
  ): Future[List[RegisteredWorkload]] = {
    // TODO: Future.traverse won't work at large scale.  Come back through and Akka Stream this later.
    for {
      listToRun <- registeredWorkloads.getRunnable
      runnables <- Future.traverse(listToRun)(registry.apply)
      managerSeq <- Future.traverse(runnables)(managers.forWorkload)
      processed <- Future.traverse(managerSeq) { manager =>
        managers.activate(manager).flatMap { mgr =>
          listToRun.find(_.id == mgr.workload.id).map(_.refresh()).get
        }
      }
    } yield processed
  }
}
