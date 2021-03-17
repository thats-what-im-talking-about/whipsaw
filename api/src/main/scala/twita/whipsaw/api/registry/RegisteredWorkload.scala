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
  * A representation of a workload that lacks the ability to actually process the load.  The RegisteredWorkload is
  * backed by the same database as the Workload trait, but does carry with it the Processor, Scheduler, and Payload
  * type information.  This is useful to parts of the library and application who don't care what the specific
  * types associated with a Workload are.  For example, the Director needs to know only that a Workload is due to
  * be run, which is knowable via the {{RegisteredWorkload.stats.runAt}} value.
  *
  * The API does not make any assumptions about the implementation of Workloads vs. RegisteredWorkloads, but it
  * should be noted that there is no exposed method for creating a {{RegisteredWorkload}} in this API.  It is
  * therefore assumed that if a Workload is created (via the {{Workloads.apply(Workload.Created(...))}} call) that this
  * workload would then be immediately retrievable by {{workloadId}} as a {{RegisteredWorkload}}.  In other words,
  * {{Workloads}} and {{RegisteredWorkloads}} are different views into the same repository.
  */
trait RegisteredWorkload extends DomainObject[EventId, RegisteredWorkload] {
  override type AllowedEvent = RegisteredWorkload.Event
  override type ObjectId = WorkloadId

  def factoryType: String
  def stats: WorkloadStatistics
  def schedulingStatus: SchedulingStatus
  def processingStatus: ProcessingStatus
  def appAttrs: JsObject
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
