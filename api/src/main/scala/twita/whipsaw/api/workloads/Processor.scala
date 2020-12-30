package twita.whipsaw.api.workloads

import java.time.Instant

import enumeratum._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

sealed trait ItemResult extends EnumEntry

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
    *             <b>Retry</b> indicates that we encountered a recoverable error.  If process() returns this result,
    *             it will delegate to {{Metadata.retryPolicy}} to figure out what to do next.
    *           </li>
    *         </ul>
    */
  def process(payload: Payload)(implicit executionContext: ExecutionContext): Future[(ItemResult, Payload)]
}
