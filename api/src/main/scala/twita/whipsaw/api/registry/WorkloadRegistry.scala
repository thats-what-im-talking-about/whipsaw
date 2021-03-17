package twita.whipsaw.api.registry

import play.api.libs.json.OFormat
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
  * Instance that is able to produce either a `Workload` instance or a `WorkloadFactory` instance for creating
  * a new `Workload`.
  */
trait WorkloadRegistry[Attr] {

  /**
    * Maps a `RegisteredWorkload` into a `Workload` so that it can be executed.
    *
    * @param rw a `RegisteredWorkload` instance, that has already been created somewhere else in the system.
    *           Typically `RegisteredWorkload` instances are obtained from the `Director`'s `RegisteredWorkload`
    *           repository.
    * @param executionContext
    * @return A `Future` which will be completed with a `Workload` object.
    */
  def apply(rw: RegisteredWorkload[Attr])(
    implicit executionContext: ExecutionContext
  ): Future[Workload[_, _, _]]

  /**
    * @param md Metadata instance that describes a `Workload` that has been defined for this system.
    * @param executionContext
    * @tparam Payload The Payload type for this `Workload`.
    * @tparam SParams The parameters that will be used to configure a `Scheduler`
    * @tparam PParams The parameters that will be used to configure a `Processor`
    * @return A `WorkloadFactory` that may be used to create new instances of this type of `Workload`.
    *
    * @see {{Metadata}}
    */
  def apply[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(
    implicit executionContext: ExecutionContext
  ): WorkloadFactory[Payload, SParams, PParams]
}
