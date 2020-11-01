package twita.whipsaw.api.engine

import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.EventIdGenerator
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait WorkloadRegistry {
  def apply(rw: RegisteredWorkload)(implicit executionContext: ExecutionContext): Future[Workload[_, _, _]]
}

/**
  * A representation of a workload that lacks the ability to actually process the load.  The RegisteredWorkload is
  * backed by the same database as the Workload trait, but offers only its {{id}} and {{factoryType}} as fields.  The
  * RegisteredWorkload may be used by a Director to instantiate the typed Workload for processing.
  */
trait RegisteredWorkload extends DomainObject[EventId, RegisteredWorkload] {
  override type AllowedEvent = RegisteredWorkload.Event
  override type ObjectId = WorkloadId

  def factoryType: String
}

object RegisteredWorkload {
  sealed class Event extends BaseEvent[EventId] with EventIdGenerator
}

trait RegisteredWorkloads extends DomainObjectGroup[EventId, RegisteredWorkload] {
  override type AllowedEvent = RegisteredWorkloads.Event

  def getRunnable: Future[List[RegisteredWorkload]]
}

object RegisteredWorkloads {
  sealed class Event extends BaseEvent[EventId] with EventIdGenerator
}
