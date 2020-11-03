package twita.whipsaw.impl.reactivemongo

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.api.DomainObjectGroup.byId
import twita.dominion.impl.reactivemongo.BaseDoc
import twita.dominion.impl.reactivemongo.Empty
import twita.dominion.impl.reactivemongo.MongoContext
import twita.dominion.impl.reactivemongo.ObjectDescriptor
import twita.dominion.impl.reactivemongo.ReactiveMongoDomainObjectGroup
import twita.dominion.impl.reactivemongo.ReactiveMongoObject
import twita.whipsaw.api.engine.RegisteredWorkload
import twita.whipsaw.api.engine.RegisteredWorkloads
import twita.whipsaw.api.engine.WorkloadRegistryEntry
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

abstract class MongoWorkloadRegistryEntry(implicit mongoContext: MongoContext) {
  self: WorkloadRegistryEntry =>

  override def forWorkloadId(id: WorkloadId)(implicit executionContext: ExecutionContext):Future[Workload[_,_,_]] =
    factory.get(byId(id)).map {
      case Some(w) => w
      case None => throw new RuntimeException("no can do")
    }

  override def factoryForMetadata[Payload: OFormat, SParams: OFormat, PParams: OFormat](
    md: Metadata[Payload, SParams, PParams]
  )(implicit executionContext: ExecutionContext): WorkloadFactory[Payload, SParams, PParams] = new MongoWorkloadFactory(md)
}

case class RegisteredWorkloadDoc(
    _id: WorkloadId
  , factoryType: String
) extends BaseDoc[WorkloadId]

object RegisteredWorkloadDoc {
  implicit val fmt = Json.format[RegisteredWorkloadDoc]
}

trait RegisteredWorkloadDescriptor
extends ObjectDescriptor[EventId, RegisteredWorkload, RegisteredWorkloadDoc]
{
  implicit def mongoContext: MongoContext

  override protected lazy val collectionName = "workloads"
  override protected def cons: Either[Empty[WorkloadId], RegisteredWorkloadDoc] => RegisteredWorkload = {
    o => new MongoRegisteredWorkload(o)
  }
}

class MongoRegisteredWorkload(protected val underlying: Either[Empty[WorkloadId], RegisteredWorkloadDoc]
)(implicit executionContext: ExecutionContext, override val mongoContext: MongoContext)
extends ReactiveMongoObject[EventId, RegisteredWorkload, RegisteredWorkloadDoc]
  with RegisteredWorkloadDescriptor
  with RegisteredWorkload
{
  override def factoryType: String = obj.factoryType
  override def apply(event: RegisteredWorkload.Event, parent: Option[BaseEvent[EventId]]): Future[RegisteredWorkload] = ???
}

class MongoRegisteredWorkloads()(
    implicit executionContext: ExecutionContext
  , val mongoContext: MongoContext
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