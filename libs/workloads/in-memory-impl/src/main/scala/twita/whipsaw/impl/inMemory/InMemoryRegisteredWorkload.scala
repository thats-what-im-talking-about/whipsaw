package twita.whipsaw.impl.inMemory

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.registry.RegisteredWorkload
import twita.whipsaw.api.registry.RegisteredWorkloads
import twita.whipsaw.api.registry.WorkloadRegistryEntry
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

abstract class InMemoryWorkloadRegistryEntry extends WorkloadRegistryEntry {
  override def forWorkloadId(id: WorkloadId)(implicit executionContext: ExecutionContext):Future[Workload[_,_,_]] =
    factory.get(byId(id)).map {
      case Some(w) => w
      case None => throw new RuntimeException("no can do")
    }

  override def factoryForMetadata[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(implicit executionContext: ExecutionContext): WorkloadFactory[Payload, SParams, PParams] = new InMemoryWorkloadFactory(md)
}

case class RegisteredWorkloadDoc(
    _id: WorkloadId
  , factoryType: String
)

trait RegisteredWorkloadDescriptor
{
  protected def cons: Either[Empty[WorkloadId], RegisteredWorkloadDoc] => RegisteredWorkload = {
    o => new InMemoryRegisteredWorkload(o)
  }
}

class InMemoryRegisteredWorkload(protected val underlying: Either[Empty[WorkloadId], RegisteredWorkloadDoc]
)(implicit executionContext: ExecutionContext) extends RegisteredWorkloadDescriptor with RegisteredWorkload
{
  protected lazy val obj: RegisteredWorkloadDoc = underlying.right.getOrElse(throw new IllegalStateException(s"Object deleted: ${id}"))
  override def factoryType: String = obj.factoryType
  override def apply(event: RegisteredWorkload.Event, parent: Option[BaseEvent[EventId]]): Future[RegisteredWorkload] = ???
}

class MongoRegisteredWorkloads()(
    implicit executionContext: ExecutionContext
)
extends ReactiveMongoDomainObjectGroup[EventId, RegisteredWorkload, RegisteredWorkloadDoc]
  with RegisteredWorkloadDescriptor
  with RegisteredWorkloads
{
  override protected def listConstraint: JsObject = Json.obj()
  override def list(q: DomainObjectGroup.Query): Future[List[RegisteredWorkload]] = ???

  override def getRunnable: Future[List[RegisteredWorkload]] = getListByJsonCrit(Json.obj("schedulingStatus" -> SchedulingStatus.Init))

  override def apply(event: RegisteredWorkloads.Event, parent: Option[BaseEvent[EventId]]): Future[RegisteredWorkload] = ???
}
