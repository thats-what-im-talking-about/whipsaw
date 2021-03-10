package twita.whipsaw.api.workloads

import java.util.UUID

import akka.actor.ActorRef
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import enumeratum.PlayJsonEnum
import enumeratum._
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
import twita.whipsaw.monitor.WorkloadStatistics

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkloadId(value: String) extends AnyVal
object WorkloadId {
  def apply(): WorkloadId = WorkloadId(UUID.randomUUID().toString)
  implicit val fmt = new Format[WorkloadId] {
    override def reads(json: JsValue): JsResult[WorkloadId] = json match {
      case JsString(id) => JsSuccess(WorkloadId(id))
      case err          => JsError(s"Expected a String but got ${err}")
    }

    override def writes(o: WorkloadId): JsValue = JsString(o.value)
  }
}

sealed trait SchedulingStatus extends EnumEntry
object SchedulingStatus
    extends Enum[SchedulingStatus]
    with PlayJsonEnum[SchedulingStatus] {
  val values = findValues

  case object Init extends SchedulingStatus
  case object Running extends SchedulingStatus
  case object Completed extends SchedulingStatus
}

sealed trait ProcessingStatus extends EnumEntry
object ProcessingStatus
    extends Enum[ProcessingStatus]
    with PlayJsonEnum[ProcessingStatus] {
  val values = findValues

  case object Init extends ProcessingStatus
  case object Running extends ProcessingStatus
  case object Waiting extends ProcessingStatus
  case object Completed extends ProcessingStatus
}

trait WorkloadContext

/**
  * Defines the contracts for describing a generic Payload in this system.  A Workload has 3 different parts that
  * need to be known in order for the Workload to run:
  * <ul>
  *   <li>
  *     A Workload Scheduler will be used as the source for all of the WorkItems that need to be processed
  *     by this Workload.  When the engine first picks up the Workload, it will invoke the scheduler and add the
  *     resulting WorkItems to the collection of items that are to be managed by this workload.
  *   </li>
  *   <li>
  *     A WorkItem list will be stored by this workload, and as the processing happens the WorkItems will be updated
  *     to reflect the current state of the item.  Most notably, all WorkItems will have a {{runAt}} parameter which
  *     gives a time that this WorkItem may be run.  This gives us a way to tell which WorkItems are due to be run
  *     and it gives us a way to delay the processing of a single item to a later time.
  *   </li>
  *   <li>
  *     A WorkItemProcessor will also be bound to this workflow and it will provide us with the knowledge of how to
  *     process each item in the Workload.  The WorkItemProcessor's job will be to do as much as can be done to the
  *     WorkItem as possible - processing it to completion, error, or delay.
  *   </li>
  * <ul>
  *
  * @tparam Payload The type that contains a complete description of everything that an individual WorkItem needs
  *                 in order to be processed by the Workload.  This will be persisted with the Workload so that the
  *                 processing of the Workload may pick up where it left off if for some reason it's interrupted.
  */
trait Workload[Payload, SParams, PParams]
    extends DomainObject[EventId, Workload[Payload, SParams, PParams]] {
  override type AllowedEvent = Event
  override type ObjectId = WorkloadId

  def name: String

  def workItems: WorkItems[Payload]
  def scheduler: Scheduler[Payload]
  def processor: Processor[Payload]
  def metadata: Metadata[Payload, SParams, PParams]
  def schedulingStatus: SchedulingStatus
  def processingStatus: ProcessingStatus
  def batchSize: Int = 100
  def desiredNumWorkers: Int = 50

  def stats: WorkloadStatistics

  // This is needed to prevent a compiler error that deals with the generic expansion of the various events that
  // are part of this trait:
  // type mismatch;
  // [error]  found   : twita.whipsaw.api.workloads.WorkItems[Payload]#WorkItemAdded
  // [error]  required: qual$1.AllowedEvent
  private lazy val workItemFactory = workItems

  def schedule(
    statsTracker: ActorRef
  )(implicit ec: ExecutionContext, m: Materializer): Future[SchedulingStatus] =
    for {
      payloadIterator <- scheduler.schedule()
      source = Source
        .fromIterator(() => payloadIterator)
        .mapAsyncUnordered(10) { payload =>
          statsTracker ! WorkloadStatistics(scheduled = 1)
          // TODO: duplicates need to be handled more specifically that Throwable during scheduling.
          workItemFactory(workItemFactory.WorkItemAdded(payload))
            .map(Option(_))
            .recoverWith {
              case t: Throwable =>
                scheduler.handleDuplicate(payload).map(_ => None)
            }
        }
      result <- source.run().map(_ => SchedulingStatus.Completed)
    } yield result

  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class ScheduleStatusUpdated(schedulingStatus: SchedulingStatus)
      extends Event
  object ScheduleStatusUpdated {
    implicit val fmt = Json.format[ScheduleStatusUpdated]
  }

  case class ProcessingStatusUpdated(processingStatus: ProcessingStatus)
      extends Event
  object ProcessingStatusUpdated {
    implicit val fmt = Json.format[ProcessingStatusUpdated]
  }

  case class StatsUpdated(stats: WorkloadStatistics) extends Event
  object StatsUpdated { implicit val fmt = Json.format[StatsUpdated] }
}

trait WorkloadFactory[Payload, SParams, PParams]
    extends DomainObjectGroup[EventId, Workload[Payload, SParams, PParams]] {
  implicit def spFmt: OFormat[SParams]
  implicit def ppFmt: OFormat[PParams]
  override type AllowedEvent = Event

  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class Created(name: String,
                     schedulerParams: SParams,
                     processorParams: PParams)
      extends Event
  object Created { implicit val fmt = Json.format[Created] }
}
