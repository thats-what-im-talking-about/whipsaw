package twita.whipsaw.api.registry

import play.api.libs.json.JsObject
import play.api.libs.json.Json.JsValueWrapper
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup
import twita.dominion.api.DomainObjectGroup.Query
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.EventIdGenerator
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.monitor.WorkloadStatistics

import scala.concurrent.Future

/**
  * A representation of a `Workload` that lacks the ability to actually process the load.  The difference
  * between a `RegisteredWorkload` and a `Workload` is that the `RegisteredWorkload` just represents an entry
  * in our `RegisteredWorkloads` repository - essentially a row in a database - but lacks the Processor,
  * Scheduler, and Payload type information.  This is useful to parts of the library and application that
  * don't care what the specific types associated with a `Workload` are.  For example, the `Director` needs to
  * know only that a `Workload` is due to be run, which only requires a check against the `stats.runAt` field here.
  *
  * The API does not make any assumptions about the implementation of `Workloads` vs. `RegisteredWorkloads`, but it
  * should be noted that the API exposes no method for creating a `RegisteredWorkload` in this API.  It is
  * therefore assumed that if a `Workload` is created (via the `Workloads.apply(Workload.Created(...))` call) that this
  * `Workload` would then be immediately retrievable by `workloadId` as a `RegisteredWorkload`.  In other words,
  * `Workload`s and `RegisteredWorkload`s are different views onto the same underlying object.
  */
trait RegisteredWorkload extends DomainObject[EventId, RegisteredWorkload] {
  override type AllowedEvent = RegisteredWorkload.Event
  override type ObjectId = WorkloadId

  /**
    * The `factoryType` field that was present in the `Metadata` instance that was initially used to create this
    * `Workload`.
    *
    * @return String that refers to the type of `Workload` this is.
    */
  def factoryType: String

  /**
    * Statistics object that corresponds to this `Workload`.
    *
    * @return Current `WorkloadStatistics` for this `Workload`.
    */
  def stats: WorkloadStatistics

  /**
    * The status of the scheduling part of this `Workload`, which is updated during the execution of the `Workload`.
    *
    * @return Current `SchedulingStatus` value.
    */
  def schedulingStatus: SchedulingStatus

  /**
    * The status of the processing part of this `Workload`, which is updated during the execution of the `Workload`.
    *
    * @return Current `ProcessingStatus` value.
    */
  def processingStatus: ProcessingStatus

  /**
    * Temporary.  This field is used to augment `Workload` data with application-specific attributes, but should be
    * done in a more type safe way.
    */
  def appAttrs: JsObject

  /**
    * Convenience method for pulling the content of this object from the database again.
    */
  def refresh(): Future[RegisteredWorkload]
}

object RegisteredWorkload {
  sealed class Event extends BaseEvent[EventId] with EventIdGenerator
}

trait RegisteredWorkloads
    extends DomainObjectGroup[EventId, RegisteredWorkload] {
  override type AllowedEvent = RegisteredWorkloads.Event

  def getRunnable: Future[List[RegisteredWorkload]]
}

object RegisteredWorkloads {
  sealed class Event extends BaseEvent[EventId] with EventIdGenerator

  // TODO: AppAttrs should be re-implemented as a generic to prevent this type-less stuff below.
  case class byAppAttrs(kvs: (String, JsValueWrapper)*) extends Query
}
