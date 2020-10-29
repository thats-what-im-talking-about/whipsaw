package twita.whipsaw.api.workloads

import java.util.UUID

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

case class WorkloadId(value: String) extends AnyVal
object WorkloadId {
  def apply(): WorkloadId = WorkloadId(UUID.randomUUID().toString)
  implicit val fmt = new Format[WorkloadId] {
    override def reads(json: JsValue): JsResult[WorkloadId] = json match {
      case JsString(id) => JsSuccess(WorkloadId(id))
      case err => JsError(s"Expected a String but got ${err}")
    }

    override def writes(o: WorkloadId): JsValue = JsString(o.value)
  }
}

/**
  * Workload instances will always be created with an instance of this class which provides the factories that create
  * the correct Scheduler and Processor for this workload.
  * @param scheduler {{RegisteredScheduler}} instance which, given an instance of {{SParams}} will create a new
  *                  {{WorkloadScheduler}} instance for use by this {{Workload}}.
  * @param processor {{RegisteredProcessor}} instance which, given an instance of {{PParams}} will create a new
  *                  {{WorkItemProcessor}} instance for use by this {{Workload}}.
  * @param payloadUniqueConstraint List of fields <b>in the Payload</b> that will be used to create the uniqueness
  *                                constraint for workItems.
  * @tparam Payload
  * @tparam SParams
  * @tparam PParams
  */
case class Metadata[Payload, SParams, PParams](
    scheduler: SParams => Scheduler[Payload]
  , processor: PParams => Processor[Payload]
  , payloadUniqueConstraint: Seq[String]
) {
  assert(payloadUniqueConstraint.size > 0,
    "In order for a scheduler to be restartable, you must specify the payload fields used to determine uniqueness.")
}

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
extends DomainObject[EventId, Workload[Payload, SParams, PParams]]
{
  override type AllowedEvent = Workload.Event
  override type ObjectId = WorkloadId

  def name: String

  def workItems: WorkItems[Payload]
  def scheduler: Scheduler[Payload]
  def processor: Processor[Payload]
  def metadata: Metadata[Payload, SParams, PParams]
}

object Workload {
  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator
}

trait WorkloadFactory[Payload, SParams, PParams]
extends DomainObjectGroup[EventId, Workload[Payload, SParams, PParams]] {
  implicit def spFmt: OFormat[SParams]
  implicit def ppFmt: OFormat[PParams]
  override type AllowedEvent = Event

  sealed trait Event extends BaseEvent[EventId] with EventIdGenerator

  case class Created(name: String, schedulerParams: SParams, processorParams: PParams) extends Event
  object Created { implicit val fmt = Json.format[Created] }
}
