package twita.whipsaw.api.workloads

import java.time.Instant

import enumeratum._

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
  /**
    * @param payload App-specific instance of a payload that is being processed
    * @return Eventually, an updated version of the payload as well as an ItemResult that will instruct the
    *         Workload engine how to proceed.
    */
  def process(payload: Payload): Future[(ItemResult, Payload)]
}
