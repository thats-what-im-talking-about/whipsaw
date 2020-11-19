package twita.whipsaw.api.engine

import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObject
import twita.dominion.api.DomainObjectGroup
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.EventIdGenerator
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Interface contract for an entry in the {{WorkloadRegistry}}.  When implementers develop new types of workloads,
  * they also need to create corresponding {{WorkloadRegistryEntry}} instances so that these new types of workloads
  * may be managed by the system.
  */
trait WorkloadRegistryEntry {
  /**
    * @return A {{WorkloadFactory}} instance that may be used to create a new instance of {{Workload}} represented
    *         by this entry.
    */
  def factory: WorkloadFactory[_,_,_]

  /**
    * @return The {{Metadata}} instance that corresponds to the {{Workload}} represented by this entry.  {{Workload}}
    *         classes are generic over 3 types: the Payloads that are processed for each WorkItem, the Scheduler that
    *         knows how to materialize all of the Payloads, and a Processor that will be used to process all of the
    *         payloads.  The Metadata instance pulls all of these types together and provides a complete representation
    *         of the application-specific processing that must happen for each type of Workload.
    */
  def metadata: Metadata[_, _, _]

  /**
    * @param id The id of a {{Workload}} that has already been created in the system.
    * @param executionContext
    * @return Asynchronously returns a {{Workload}} instance.  Note that the generic types of the Workload are not
    *         known.  The idea here is that there are a handful of methods on {{Workload}} interface that may be
    *         executed without knowledge of what the actual concrete implementation's generic types are.  These methods
    *         are used by the workload engines to interact with the workload, but the engine itself doesn't know or
    *         care what the generic types actually are.
    */
  def forWorkloadId(id: WorkloadId)(implicit executionContext: ExecutionContext): Future[Workload[_, _, _]]

  /**
    * @param md {{Metadata}} instance that we will use to look up the Workload factory.  Metadata instances are created
    *           by developers as they code new types of {{Workload}} classes.  The {{Metadata}} instance that is
    *           created as part of the development process should be created as a singleton that is accessible both in
    *           the application that will be instantiating new workloads and in the engine that will be materializing
    *           and executing the Workloads.
    *
    * @param executionContext
    * @tparam Payload Arbitrary type that contains all of the information that is needed to process an individual
    *                 {{WorkItem}}.  The Payload is what makes the item unique in this Workload.  For example, if we
    *                 are creating a {{Workload}} that knows how to send email to a bunch of users, the Payload should
    *                 contain at the very least the email address of the user.
    * @tparam SParams Parameters that need to be passed into the scheduler instance in order to materialize the list of
    *                 Payloads that are to be processed as a part of this {{Workload}}.
    * @tparam PParams Parameters that need to be passed into the processor instance in order to process the payloads.
    * @return A {{WorkloadFactory}} with the generics statically known at the callpoint of this function.
    */
  def factoryForMetadata[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(implicit executionContext: ExecutionContext): WorkloadFactory[Payload, SParams, PParams]
}

trait WorkloadRegistry {
  def apply(rw: RegisteredWorkload)(implicit executionContext: ExecutionContext): Future[Workload[_, _, _]]

  def apply[Payload: OFormat, SParams: OFormat, PParams: OFormat](md: Metadata[Payload, SParams, PParams])(
    implicit executionContext: ExecutionContext
  ): WorkloadFactory[Payload, SParams, PParams]
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
