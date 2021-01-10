package twita.whipsaw.api.registry

import play.api.libs.json.OFormat
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait WorkloadRegistry {
  def apply(rw: RegisteredWorkload)(implicit executionContext: ExecutionContext): Future[Workload[_, _, _]]

  def apply[Payload: OFormat, SParams: OFormat, PParams: OFormat](md: Metadata[Payload, SParams, PParams])(
    implicit executionContext: ExecutionContext
  ): WorkloadFactory[Payload, SParams, PParams]
}
