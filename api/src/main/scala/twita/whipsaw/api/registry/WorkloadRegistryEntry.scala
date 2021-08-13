package twita.whipsaw.api.registry

import play.api.libs.json.OFormat
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Describes an entry in the [[WorkloadRegistry]].  When implementers develop new types of workloads,
  * they also need to create corresponding WorkloadRegistryEntry instances so that these new types of workloads
  * may be managed by the system.
  */
trait WorkloadRegistryEntry {

  /**
    * Defines a WorkloadFactory instance that may be used to create a new instance of the Workload represented
    * by this entry.
    *
    * @return A WorkloadFactory instance
    */
  def factory: WorkloadFactory[_, _, _]

  /**
    * Defines the Metadata instance that corresponds to the Workload represented by this entry.  Workload
    * classes are generic over 3 types: the Payloads that are processed for each WorkItem, the Scheduler that
    * knows how to materialize all of the Payloads, and a Processor that will be used to process all of the
    * payloads.  The Metadata instance pulls all of these types together and provides a complete representation
    * of the application-specific processing that must happen for each type of Workload.
    *
    * @return Metadata instance that describes this Workload
    */
  def metadata: Metadata[_, _, _]

  /**
    * Returns a Future that is to be completed by a Workload that has already been added to the system.
    * Note that the generic types of the Workload are not known.  The idea here is that there are a handful
    * of methods on Workload interface that may be executed without knowledge of what the actual concrete
    * implementation's generic types are.  These methods are used by the Workload Engine to interact with the
    * Workload, but the engine itself doesn't know or care what the generic types actually are.
    *
    * @param id The id of a Workload that has already been created in the system.
    * @param executionContext
    * @return A Future to be completed by the Workload that corresponds to the WorkloadId if one exists.
    */
  def forWorkloadId(id: WorkloadId)(
    implicit executionContext: ExecutionContext
  ): Future[Option[Workload[_, _, _]]]

  /**
    * Returns a WorkloadFactory given a Metadata instance.   Metadata instances are created by developers as they
    * code new types of Workload classes.  The Metadata instance that is created as part of the development process
    * should be created as a singleton that is accessible both in the application that will be originating new
    * Workloads and in the Engine that will be materializing and executing the Workloads.  Unlike
    * `WorkloadRegistryEntry.forWorkloadId()`, this method does in fact fill in the generic types for the Workload.
    *
    * @param md `Metadata` instance that we will use to look up the Workload factory.
    * @param executionContext
    * @tparam Payload Arbitrary type that contains all of the information that is needed to process an individual
    *                 `WorkItem`.  The Payload is what makes the item unique in this `Workload`.  For example, if we
    *                 are creating a `Workload` that knows how to send email to a bunch of users, the Payload should
    *                 contain at the very least the email address of the user.
    * @tparam SParams Parameters that need to be passed into the scheduler instance in order to materialize the list of
    *                 Payloads that are to be processed as a part of this `Workload`.
    * @tparam PParams Parameters that need to be passed into the processor instance in order to process the payloads.
    * @return A `WorkloadFactory` that corresponds to the provided `Metadata` instance.
    */
  def factoryForMetadata[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(
    implicit executionContext: ExecutionContext
  ): WorkloadFactory[Payload, SParams, PParams]
}
