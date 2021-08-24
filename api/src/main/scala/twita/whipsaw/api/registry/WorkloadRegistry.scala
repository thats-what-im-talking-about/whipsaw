package twita.whipsaw.api.registry

import play.api.libs.json.OFormat
import twita.whipsaw.api.WorkloadError
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Provides descriptions of all of the different application-specific `Workload` types that are available in
  * this system.  The main function of the `WorkloadRegistry` is to provide applications access to the Workloads
  * that are currently defined in the system.  The registry provides two lookup types:
  *
  * # For `Workloads` that have already been instantiated and stored in the system, there will be an associated
  *   `RegisteredWorkload` instance which may be found, for example, by querying the `RegisteredWorkloads`
  *   instance of the `Director`.  This instance may then be used to obtain the actual `Workload` instance that
  *   is used to run the `Workload`.
  * # For `Workload`s that need to be instantiated, a factory can be obtained from this `WorkloadRegistry` by
  *   providing a `Metadata` instance that describes the `Workload` you are looking for.
  */
trait WorkloadRegistry {

  /**
    * Maps a `RegisteredWorkload` into a `Workload` so that it can be executed.
    *
    * @param rw a `RegisteredWorkload` instance, that has already been created somewhere else in the system.
    *           Typically `RegisteredWorkload` instances are obtained from the `Director`'s `RegisteredWorkload`
    *           repository.
    * @param executionContext
    * @return A `Future` which will be completed either with a `Right(Workload)` instance or a `Left(WorkloadError)`
    *         if there is some error or if the `Workload` wasn't found.  Note that this could have been implemented
    *         as an `Option` instead of an `Either`, but the fact is that if you have a valid instance of a
    *         `RegisteredWorkload`, you should always be able to get an instance of `Workload[_, _, _]` so making this
    *         an `Either` is a stronger statement that not finding the `Workload` is indeed an error.
    */
  def apply(rw: RegisteredWorkload)(
    implicit executionContext: ExecutionContext
  ): Future[Either[WorkloadError, Workload[_, _, _]]]

  /**
    * @param md Metadata instance that describes a `Workload` that has been defined for this system.
    * @param executionContext
    * @tparam Payload The Payload type for this `Workload`.
    * @tparam SParams The parameters that will be used to configure a `Scheduler`
    * @tparam PParams The parameters that will be used to configure a `Processor`
    *
    * @return A `Future` which will be completed either with a `Right(Workload)` instance or a `Left(WorkloadError)`
    *         if there is some error or if the `Workload` wasn't found.  Note that this could have been implemented
    *         as an `Option` instead of an `Either`, but the fact is that if you have a valid Metadata instance
    *         you should always be able to get an instance of `Workload[_, _, _]` so making this
    *         an `Either` is a stronger statement that not finding the `Workload` is indeed an error.
    */
  def apply[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(
    implicit executionContext: ExecutionContext
  ): Either[WorkloadError, WorkloadFactory[Payload, SParams, PParams]]
}
