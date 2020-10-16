package twita.whipsaw.api

import java.time.Instant

import enumeratum._

sealed trait ItemResult extends EnumEntry

object ItemResult extends Enum[ItemResult] {
  val values = findValues

  case object Done extends ItemResult
  case class Error(t: Throwable) extends ItemResult
  case class Retry(t: Throwable) extends ItemResult
  case class Reschedule(runAt: Instant) extends ItemResult
}

trait WorkItemProcessor[Payload] {
  def process(item: WorkItems[Payload]): ItemResult
}