package twita.whipsaw.api.workloads

import java.time.Instant

import akka.actor.ActorRef
import enumeratum._
import twita.whipsaw.monitor.WorkloadStatistics

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

sealed trait ItemResult extends EnumEntry {
  def updateStatsWithResult(statsTracker: ActorRef): Unit = {
    this match {
      case r: ItemResult.Error =>
        statsTracker ! WorkloadStatistics.RunningToError
      case ItemResult.Done =>
        statsTracker ! WorkloadStatistics.RunningToCompleted
      case r: ItemResult.Retry =>
        statsTracker ! WorkloadStatistics.RunningToScheduledRetry
      case r: ItemResult.Reschedule =>
        statsTracker ! WorkloadStatistics.RunningToScheduled
    }
  }
}

object ItemResult extends Enum[ItemResult] with PlayJsonEnum[ItemResult] {
  val values = findValues

  case object Done extends ItemResult
  case class Error(t: Throwable) extends ItemResult
  case class Retry(t: Throwable) extends ItemResult
  case class Reschedule(runAt: Instant) extends ItemResult
}

/**
  * Trivial interface that defines the contract the implementers must fulfill in order to process work items within
  * a Workload.
  * @tparam Payload The application-defined payload for the Workload that is being processed.
  */
trait Processor[Payload] {
  // TODO: Document the remaining cases for ItemResult outcomes.
  /**
    * @param payload App-specific instance of a payload that is being processed
    * @return Eventually, an updated version of the payload as well as an ItemResult that will instruct the
    *         Workload engine how to proceed.  Note that ItemResult is an enumeration of a few possible outcomes
    *         of processing this item.  The ItemResult that is returned will have an impact on what happens to this
    *         WorkItem next.  Specifically:
    *         <ul>
    *           <li>
    *             <b>Done</b> indicates success.  The WorkItem has been successfully processed and marked as such,
    *             and the workload engine will take no further action.
    *           </li>
    *           <li>
    *             <b>Error</b> indicates an unrecoverable error occurred.  The WorkItem has been successfully
    *             processed and marked as such, and the workload engine will take no further action.
    *           </li>
    *           <li>
    *             <b>Retry</b> indicates that we encountered a recoverable error.  If process() returns this result,
    *             it will delegate to {{Metadata.retryPolicy}} to figure out what to do next.
    *           </li>
    *           <li>
    *             <b>Reschedule</b> indicates that the result of our processing is that this item is going to go
    *             to sleep until some future time (specified by the `runAt` member of this case class).
    *           </li>
    *         </ul>
    */
  def process(payload: Payload)(
    implicit executionContext: ExecutionContext
  ): Future[(ItemResult, Payload)]
}
