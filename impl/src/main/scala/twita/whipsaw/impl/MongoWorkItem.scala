package twita.whipsaw.impl

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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkItemDoc[Desc] (
    _id: WorkItemId
  , desc: Desc
  , runAt: Option[Instant] = None
) extends BaseDoc[WorkItemId]
object WorkItemDoc { implicit def fmt[Desc: Format] = Json.format[WorkItemDoc[Desc]] }

trait WorkItemDescriptor[Desc] extends ObjectDescriptor[EventId, WorkItem[Desc], WorkItemDoc[Desc]] {
  implicit def mongoContext: MongoContext
  implicit def descFmt: Format[Desc]

  override protected def objCollectionFt: Future[JSONCollection] = mongoContext.getCollection("workloads")
  override protected def cons: Either[Empty[WorkItemId], WorkItemDoc[Desc]] => WorkItem[Desc] = o => new MongoWorkItem(o)
}

class MongoWorkItem[Desc: Format](protected val underlying: Either[Empty[WorkItemId], WorkItemDoc[Desc]])(
  implicit executionContext: ExecutionContext, override val mongoContext: MongoContext
) extends ReactiveMongoObject[EventId, WorkItem[Desc], WorkItemDoc[Desc]]
  with WorkItemDescriptor[Desc]
  with WorkItem[Desc]
{
  override def descFmt = implicitly[Format[Desc]]
  override def runAt: Option[Instant] = obj.runAt
  override def description: Desc = obj.desc
  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[WorkItem[Desc]] = ???
}

class MongoWorkItems[Desc: Format](implicit executionContext: ExecutionContext, val mongoContext: MongoContext)
  extends ReactiveMongoDomainObjectGroup[EventId, WorkItem[Desc], WorkItemDoc[Desc]]
    with WorkItemDescriptor[Desc]
    with WorkItems[Desc]
{
  override def descFmt = implicitly[Format[Desc]]
  override protected def listConstraint: JsObject = Json.obj()
  override def list(q: DomainObjectGroup.Query): Future[List[WorkItem[Desc]]] = ???

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[WorkItem[Desc]] = event match {
    case evt: WorkItems.Created[Desc] =>
      create(WorkItemDoc(_id = WorkItemId(), runAt = evt.runAt, desc = evt.desc), evt, parent)
  }
}
