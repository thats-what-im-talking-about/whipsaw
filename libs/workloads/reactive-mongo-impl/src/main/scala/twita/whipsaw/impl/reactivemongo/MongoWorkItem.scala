package twita.whipsaw.impl.reactivemongo

import java.time.Instant

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import reactivemongo.akkastream.cursorProducer
import reactivemongo.api.indexes.IndexType
import reactivemongo.play.json.collection.JSONCollection
import reactivemongo.play.json.compat._
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.impl.reactivemongo.BaseDoc
import twita.dominion.impl.reactivemongo.Empty
import twita.dominion.impl.reactivemongo.MongoContext
import twita.dominion.impl.reactivemongo.ObjectDescriptor
import twita.dominion.impl.reactivemongo.ReactiveMongoDomainObjectGroup
import twita.dominion.impl.reactivemongo.ReactiveMongoObject
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.WorkItem
import twita.whipsaw.api.workloads.WorkItemId
import twita.whipsaw.api.workloads.WorkItems
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkItemDoc[Payload] (
    _id: WorkItemId
  , workloadId: WorkloadId
  , payload: Payload
  , runAt: Option[Instant] = None
  , startedProcessingAt: Option[Instant] = None
  , finishedProcessingAt: Option[Instant] = None
) extends BaseDoc[WorkItemId]
object WorkItemDoc { implicit def fmt[Payload: Format] = Json.format[WorkItemDoc[Payload]] }

trait WorkItemDescriptor[Payload] extends ObjectDescriptor[EventId, WorkItem[Payload], WorkItemDoc[Payload]] {
  implicit def mongoContext: MongoContext
  implicit def pFmt: OFormat[Payload]
  protected def workload: Workload[Payload, _, _]
  override protected lazy val collectionName = s"workloads.${workload.id.value}"
  override protected def cons: Either[Empty[WorkItemId], WorkItemDoc[Payload]] => WorkItem[Payload] = o => new MongoWorkItem(o, workload)

  override lazy val eventLogger = new MongoObjectEventStackLogger(50)
}

class MongoWorkItem[Payload: OFormat](
    protected val underlying: Either[Empty[WorkItemId], WorkItemDoc[Payload]]
  , protected val workload: Workload[Payload, _, _]
)(
  implicit executionContext: ExecutionContext, override val mongoContext: MongoContext
) extends ReactiveMongoObject[EventId, WorkItem[Payload], WorkItemDoc[Payload]]
  with WorkItemDescriptor[Payload]
  with WorkItem[Payload]
{
  override val pFmt = implicitly[OFormat[Payload]]

  override def runAt: Option[Instant] = obj.runAt
  override def payload: Payload = obj.payload

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]) = event match {
    case evt: StartedProcessing =>
      updateVerbose(
        Json.obj(
            "$set" -> Json.obj("startedProcessingAt" -> Json.toJson(evt.at))
          , "$unset" -> Json.obj("runAt" -> 1)
        ), evt, parent
      )
    case evt: FinishedProcessing =>
      updateVerbose(
        Json.obj(
          "$set" ->
            Json.obj(
                "payload" -> Json.toJsObject(evt.newPayload)
              , "finishedProcessingAt" -> Json.toJson(evt.at)
            )
        ), evt, parent
      )
  }
}

class MongoWorkItems[Payload: OFormat](protected val workload: Workload[Payload, _, _])(
  implicit executionContext: ExecutionContext, val mongoContext: MongoContext
)
  extends ReactiveMongoDomainObjectGroup[EventId, WorkItem[Payload], WorkItemDoc[Payload]]
    with WorkItemDescriptor[Payload]
    with WorkItems[Payload]
{

  override protected def ensureIndexes(coll: JSONCollection) = {
    val uniqueIndexKeys = workload.metadata.payloadUniqueConstraint.map(fld => s"payload.${fld}" -> IndexType.Ascending)
    for {
      // enforce uniqueness over all payloads in this collection
      payloadUnique <- coll.indexesManager.ensure(
        ObjectDescriptor.index(
            key = uniqueIndexKeys
          , unique = true
          , sparse = true
        )
      )
      // sparse index on runAt to speed up the retrieval of workItems that are ready to run
      runAt <- coll.indexesManager.ensure(
        ObjectDescriptor.index(
            key = Seq("runAt" -> IndexType.Ascending)
          , sparse = true
        )
      )
    } yield payloadUnique && runAt
  }

  override val pFmt = implicitly[OFormat[Payload]]
  override protected def listConstraint: JsObject = Json.obj()

  override def list(q: DomainObjectGroup.Query): Future[List[WorkItem[Payload]]] = q match {
    case WorkItems.RunnableAt(when) => getListByJsonCrit(Json.obj("runAt" -> Json.obj("$lt" -> when)))
  }

  /**
    * @return Eventually returns a list of items whose runAt is in the past.
    */
  override def runnableItemSource(implicit m: Materializer): Future[Source[WorkItem[Payload], Any]] = {
    val q = Json.obj("runAt" -> Json.obj("$lt" -> Json.toJson(Instant.now)))
    for {
      coll <- objCollectionFt
    } yield {
      coll.find(q ++ notDeletedConstraint, projection = Some(Json.obj()))
        .sort(Json.obj("runAt" -> 1))
        .cursor[WorkItemDoc[Payload]]()
        .documentSource()
        .map(doc => cons(Right(doc)))
    }
  }

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[WorkItem[Payload]] = event match {
    case evt: WorkItemAdded =>
      create(WorkItemDoc(_id = WorkItemId(), workloadId = workload.id, runAt = evt.runAt, payload = evt.payload), evt, parent)
  }
}
