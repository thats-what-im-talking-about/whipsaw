package twita.whipsaw.reactivemongo.impl

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import reactivemongo.play.json.collection.JSONCollection
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.BaseDoc
import twita.dominion.impl.reactivemongo.Empty
import twita.dominion.impl.reactivemongo.MongoContext
import twita.dominion.impl.reactivemongo.ObjectDescriptor
import twita.dominion.impl.reactivemongo.ReactiveMongoDomainObjectGroup
import twita.dominion.impl.reactivemongo.ReactiveMongoObject
import twita.whipsaw.api.EventId
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.Workload
import twita.whipsaw.api.WorkloadEngine
import twita.whipsaw.api.WorkloadId
import twita.whipsaw.api.Workloads

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkloadDoc(
    _id: WorkloadId
  , name: String
  , factoryType: String
) extends BaseDoc[WorkloadId]
object WorkloadDoc { implicit val fmt = Json.format[WorkloadDoc] }

trait WorkloadDescriptor[Payload] extends ObjectDescriptor[EventId, Workload[Payload], WorkloadDoc] {
  implicit def mongoContext: MongoContext
  implicit def payloadFmt: OFormat[Payload]

  override protected def objCollectionFt: Future[JSONCollection] = mongoContext.getCollection("workloads")
  override protected def cons: Either[Empty[WorkloadId], WorkloadDoc] => Workload[Payload] = o => new MongoWorkload[Payload](o)
}

class MongoWorkload[Payload](protected val underlying: Either[Empty[WorkloadId], WorkloadDoc])(
  implicit executionContext: ExecutionContext, override val mongoContext: MongoContext, val payloadFmt: OFormat[Payload]
) extends ReactiveMongoObject[EventId, Workload[Payload], WorkloadDoc]
  with WorkloadDescriptor[Payload]
  with Workload[Payload]
{
  override def name: String = obj.name

  override def factoryType: String = obj.factoryType

  override def workItems: WorkItems[Payload] = new MongoWorkItems[Payload](this)

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload[Payload]] = ???
}

class MongoWorkloads[Payload](implicit executionContext: ExecutionContext, val mongoContext: MongoContext, val payloadFmt: OFormat[Payload])
  extends ReactiveMongoDomainObjectGroup[EventId, Workload[Payload], WorkloadDoc]
    with WorkloadDescriptor[Payload]
    with Workloads[Payload]
{
  override protected def listConstraint: JsObject = Json.obj()
  override def list(q: DomainObjectGroup.Query): Future[List[Workload[Payload]]] = ???

  override def process(id: WorkloadId, engine: WorkloadEngine[Payload]): Future[Unit] =
    for {
      workloadOpt <- get(DomainObjectGroup.byId(id))
      result <- workloadOpt match {
        case Some(workload) => engine.process(workload)
      }
    } yield ()

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload[Payload]] = event match {
    case evt: Workloads.Created => create(
      WorkloadDoc(
          _id = WorkloadId()
        , name = evt.name
        , factoryType = this.getClass.getName
      ), evt, parent)
    }
}

