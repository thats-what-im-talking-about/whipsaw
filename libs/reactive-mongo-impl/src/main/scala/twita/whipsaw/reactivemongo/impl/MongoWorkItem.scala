package twita.whipsaw.reactivemongo.impl

import java.time.Instant

import play.api.libs.json.Format
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
import twita.whipsaw.api.WorkItem
import twita.whipsaw.api.WorkItemId
import twita.whipsaw.api.WorkItems
import twita.whipsaw.api.Workload
import twita.whipsaw.api.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkItemDoc[Payload] (
    _id: WorkItemId
  , workloadId: WorkloadId
  , payload: Payload
  , runAt: Option[Instant] = None
) extends BaseDoc[WorkItemId]
object WorkItemDoc { implicit def fmt[Payload: Format] = Json.format[WorkItemDoc[Payload]] }

trait WorkItemDescriptor[Payload] extends ObjectDescriptor[EventId, WorkItem[Payload], WorkItemDoc[Payload]] {
  implicit def mongoContext: MongoContext
  implicit def descFmt: Format[Payload]
  protected def workload: Workload[Payload]

  override protected lazy val objCollectionFt: Future[JSONCollection] = mongoContext.getCollection(s"workloads.${workload.id.value}")
  override protected def cons: Either[Empty[WorkItemId], WorkItemDoc[Payload]] => WorkItem[Payload] = o => new MongoWorkItem(o, workload)
}

class MongoWorkItem[Payload: Format](protected val underlying: Either[Empty[WorkItemId], WorkItemDoc[Payload]], protected val workload: Workload[Payload])(
  implicit executionContext: ExecutionContext, override val mongoContext: MongoContext
) extends ReactiveMongoObject[EventId, WorkItem[Payload], WorkItemDoc[Payload]]
  with WorkItemDescriptor[Payload]
  with WorkItem[Payload]
{
  override def descFmt = implicitly[Format[Payload]]
  override def runAt: Option[Instant] = obj.runAt
  override def payload: Payload = obj.payload
  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[WorkItem[Payload]] = ???
}

class MongoWorkItems[Payload: Format](protected val workload: Workload[Payload])(implicit executionContext: ExecutionContext, val mongoContext: MongoContext)
  extends ReactiveMongoDomainObjectGroup[EventId, WorkItem[Payload], WorkItemDoc[Payload]]
    with WorkItemDescriptor[Payload]
    with WorkItems[Payload]
{
  override def descFmt = implicitly[Format[Payload]]
  override protected def listConstraint: JsObject = Json.obj()
  override def list(q: DomainObjectGroup.Query): Future[List[WorkItem[Payload]]] = ???

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[WorkItem[Payload]] = event match {
    case evt: WorkItems.WorkItemAdded[Payload] =>
      create(WorkItemDoc(_id = WorkItemId(), workloadId = workload.id, runAt = evt.runAt, payload = evt.payload), evt, parent)
  }
}
