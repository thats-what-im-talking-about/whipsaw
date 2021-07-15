package twita.whipsaw.api.workloads

import akka.NotUsed
import akka.stream.scaladsl.Source

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Encapsulates the logic for scheduling a particular Workload.  In this context, the term "scheduling" refers to
  * producing an arbitrarily large number of WorkItems which are to be added to a Workload.  Presumably these items
  * would all also have a `runAt` time attached to them, so that an engine run against this Workload would know to
  * process the item when the time comes.
  *
  * Workload scheduling may in itself be a long-running process.  For example, if the workItems are produced as a
  * result of processing a huge result set, it is the job of the scheduler to issue the query and process that
  * result set.  Because this scheduling process may take a long time, it is also possible that this process may
  * be interrupted before it is completed.  Therefore, it is required that each scheduling task be restartable.
  * Restartability may take one of two different forms.  Either we are able to save checkpoints and somehow restart
  * the scheduling job from where it left off or we are able to restart the job from the beginning and not have any
  * duplicate workItems be scheduled.  Which form a particular Workload uses is left to the implementation.
  */
trait Scheduler[Payload] {

  /**
    * Main function for finding the set of Payloads that need to be processed as part of this [[Workload]].  The
    * Workload Management framework calls this function in order to populate the [[Workload]] with work to execute.
    *
    * @param ec
    * @return A Future that will be completed with a [[Source]] which will be processed by the frwmework.
    */
  def schedule()(
    implicit ec: ExecutionContext
  ): Future[Source[Payload, NotUsed]]

  /**
    * Payloads tied to a particular [[Workload]] should be unique on some attribute as specified in the [[Metadata]]
    * class.  If for some reason the [[Scheduler]] provides the framework with a Payload that has already been
    * scheduled (as would happen on a [[Scheduler]] restart), the framework will call this method to execute any
    * Workload-specific processing that needs to happen.
    *
    * @param payload The duplicate Payload that was found.
    * @param ec
    * @return A Future that will complete will successfully complete with a Unit when scheduling is complete.
    */
  def handleDuplicate(payload: Payload)(
    implicit ec: ExecutionContext
  ): Future[Unit] = Future.unit
}
