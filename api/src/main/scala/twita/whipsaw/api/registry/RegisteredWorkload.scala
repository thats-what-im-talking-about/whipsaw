package twita.whipsaw.api.registry

import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.EventIdGenerator
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkloadId
import twita.whipsaw.monitor.WorkloadStatistics

import scala.concurrent.Future

/**
  * A representation of a workload that lacks the ability to actually process the load.  The RegisteredWorkload is
  * backed by the same database as the Workload trait, but offers only its {{id}} and {{factoryType}} as fields.  The
  * RegisteredWorkload may be used by a Director to instantiate the typed Workload for processing.
  */
trait RegisteredWorkload extends DomainObject[EventId, RegisteredWorkload] {
  override type AllowedEvent = RegisteredWorkload.Event
  override type ObjectId = WorkloadId

  def factoryType: String
  def stats: WorkloadStatistics
  def schedulingStatus: SchedulingStatus
  def processingStatus: ProcessingStatus
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
}
