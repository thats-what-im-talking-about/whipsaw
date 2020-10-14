package twita.whipsaw.impl

import play.api.libs.json.JsObject
import play.api.libs.json.Json
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
import twita.whipsaw.api.Workload
import twita.whipsaw.api.WorkloadId
import twita.whipsaw.api.Workloads

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkloadDoc(
    _id: WorkloadId
  , name: String
) extends BaseDoc[WorkloadId]
object WorkloadDoc { implicit val fmt = Json.format[WorkloadDoc] }

trait WorkloadDescriptor extends ObjectDescriptor[EventId, Workload, WorkloadDoc] {
  implicit def mongoContext: MongoContext

  override protected def objCollectionFt: Future[JSONCollection] = mongoContext.getCollection("workloads")
  override protected def cons: Either[Empty[WorkloadId], WorkloadDoc] => Workload = o => new MongoWorkload(o)
}

class MongoWorkload(protected val underlying: Either[Empty[WorkloadId], WorkloadDoc])(
  implicit executionContext: ExecutionContext, override val mongoContext: MongoContext
) extends ReactiveMongoObject[EventId, Workload, WorkloadDoc]
  with WorkloadDescriptor
  with Workload
{
  override def name: String = obj.name
  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload] = ???
}

class MongoWorkloads(implicit executionContext: ExecutionContext, val mongoContext: MongoContext)
  extends ReactiveMongoDomainObjectGroup[EventId, Workload, WorkloadDoc]
    with WorkloadDescriptor
    with Workloads
{
  override protected def listConstraint: JsObject = Json.obj()
  override def list(q: DomainObjectGroup.Query): Future[List[Workload]] = ???

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload] = event match {
    case evt: Workloads.Created => create(
      WorkloadDoc(
          _id = WorkloadId()
        , name = evt.name
      ), evt, parent)
    }
}

