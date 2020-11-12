package twita.whipsaw.api.workloads

import java.time.Instant
import java.util.UUID

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup
import twita.dominion.api.DomainObjectGroup.Query
import twita.dominion.api.EmptyEventFmt
import twita.whipsaw.api.workloads.ItemResult.Done
import twita.whipsaw.api.workloads.ItemResult.Retry

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkItemId(value: String) extends AnyVal
object WorkItemId {
  def apply(): WorkItemId = WorkItemId(UUID.randomUUID().toString)
  implicit val fmt = new Format[WorkItemId] {
    override def reads(json: JsValue): JsResult[WorkItemId] = json match {
      case JsString(id) => JsSuccess(WorkItemId(id))
      case err => JsError(s"Expected a String but got ${err}")
    }

    override def writes(o: WorkItemId): JsValue = JsString(o.value)
  }
}

/**
  * This trait describes a single WorkItem that may be added to a Workload.
  * WorkItems will have attributes that are generic and needed by the framework
  * (e.g. runAt) and they will also have application specific attributes.
  *
  * @tparam Payload case class that contains application specific data about this
  *              WorkItem.
  */
trait WorkItem[Payload] extends DomainObject[EventId, WorkItem[Payload]] {
  implicit def pFmt: OFormat[Payload]
  override type AllowedEvent = Event
  override type ObjectId = WorkItemId

  /**
    * @return If this item needs to be processed, this method returns the earliest time at which the item should be
    *         run.  This is how delays in scheduling are handled.  If the item is completed, runAt will be None and
    *         the scheduler will no longer pick up this item.
    */
  def runAt: Option[Instant]

  /**
    * @return Return the number of consecutive times that this item has been tried.  Note that when an item
    *         successfully processes the retry count will always be reset to 0.  So, for example, if an item fails
    *         and is retried but then is successfully processed and rescheduled, the event history will contain the
    *         failures but the current retry count will be reset back to 0.
    */
  def retryCount: Int

  /**
    * @return Implementer-provided description of this work item.
    */
  def payload: Payload

  protected def workload: Workload[Payload, _, _]

  def process()(implicit ec: ExecutionContext): Future[ItemResult] = for {
    started <- this(StartedProcessing())
    (itemResult, updatedPayload) <- workload.processor.process(payload)
    result <- itemResult match {
      case Done => this(FinishedProcessing(updatedPayload, itemResult))
      case Retry(t) => workload.metadata.retryPolicy.retry(this)
    }
  } yield itemResult

  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class StartedProcessing(at: Instant = Instant.now) extends Event
  object StartedProcessing { implicit val fmt = Json.format[StartedProcessing] }

  case class FinishedProcessing(newPayload: Payload, result: ItemResult, at: Instant = Instant.now) extends Event
  object FinishedProcessing { implicit val fmt = Json.format[FinishedProcessing] }

  case class RetryScheduled(at: Instant, tryNumber: Int) extends Event
  object RetryScheduled { implicit val fmt = Json.format[RetryScheduled] }

  case class MaxRetriesReached() extends Event
  object MaxRetriesReached { implicit val fmt = EmptyEventFmt(MaxRetriesReached()) }
}

trait WorkItems[Payload] extends DomainObjectGroup[EventId, WorkItem[Payload]] {
  implicit def pFmt: OFormat[Payload]
  override type AllowedEvent = Event

  /**
    * @return Eventually returns a list of items whose runAt is in the past.
    */
  def runnableItemSource(runAt: Instant, batchSize: Int)(implicit m: Materializer): Future[Source[WorkItem[Payload], Any]]

  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class WorkItemAdded(payload: Payload, runAt: Option[Instant] = Some(Instant.now())) extends Event
  object WorkItemAdded { implicit val fmt = Json.format[WorkItemAdded] }
}

object WorkItems {
  case class RunnableAt(runAt: Instant = Instant.now) extends Query
  object RunnableAt { implicit val fmt = Json.format[RunnableAt] }
}
