package twita.whipsaw.impl.reactivemongo

import java.time.Instant

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
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
import twita.whipsaw.api.workloads.WorkItemStatus
import twita.whipsaw.api.workloads.WorkItemStatus.Scheduled
import twita.whipsaw.api.workloads.WorkItems
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadContext
import twita.whipsaw.api.workloads.WorkloadId

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkItemDoc[Payload](
  _id: WorkItemId,
  workloadId: WorkloadId,
  payload: Payload,
  runAt: Option[Instant] = None,
  retryCount: Int = 0,
  status: Option[WorkItemStatus] = None,
  startedProcessingAt: Option[Instant] = None,
  finishedProcessingAt: Option[Instant] = None,
  errorStoppedProcessingAt: Option[Instant] = None,
  _eventStack: Option[Seq[JsObject]] = None,
) extends BaseDoc[WorkItemId]
object WorkItemDoc {
  implicit def fmt[Payload: Format] = Json.format[WorkItemDoc[Payload]]
}

trait WorkItemDescriptor[Payload]
    extends ObjectDescriptor[EventId, WorkItem[Payload], WorkItemDoc[Payload]] {
  implicit def mongoContext: MongoContext with WorkloadContext
  implicit def pFmt: OFormat[Payload]
  protected def workload: Workload[Payload, _, _]
  override protected lazy val collectionName = s"workloads.${workload.id.value}"
  override protected def cons
    : Either[Empty[WorkItemId], WorkItemDoc[Payload]] => WorkItem[Payload] =
    o => new MongoWorkItem(o, workload)

  override lazy val eventLogger = new MongoObjectEventStackLogger(50)
}

class MongoWorkItem[Payload: OFormat](
  protected val underlying: Either[Empty[WorkItemId], WorkItemDoc[Payload]],
  protected val workload: Workload[Payload, _, _]
)(implicit executionContext: ExecutionContext,
  override val mongoContext: MongoContext with WorkloadContext)
    extends ReactiveMongoObject[EventId, WorkItem[Payload], WorkItemDoc[
      Payload
    ]]
    with WorkItemDescriptor[Payload]
    with WorkItem[Payload] {
  override val pFmt = implicitly[OFormat[Payload]]

  override def runAt: Option[Instant] = obj.runAt
  override def payload: Payload = obj.payload
  override def retryCount: Int = obj.retryCount
  override def status: Option[WorkItemStatus] = obj.status
  override def _eventStack: Option[Seq[JsonTime]] = obj._eventStack

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]) =
    event match {
      case evt: StartedProcessing =>
        updateVerbose(
          Json.obj(
            "$set" -> Json.obj(
              "startedProcessingAt" -> Json.toJson(evt.at),
              "status" -> Json.toJson(WorkItemStatus.Running)
            ),
            "$unset" -> Json.obj("runAt" -> 1, "finishedProcessingAt" -> 1)
          ),
          evt,
          parent
        )
      case evt: FinishedProcessing =>
        updateVerbose(
          Json.obj(
            "$set" -> Json.obj(
              "payload" -> Json.toJsObject(evt.newPayload),
              "finishedProcessingAt" -> Json.toJson(evt.at),
              "status" -> Json.toJson(WorkItemStatus.Completed),
              "retryCount" -> Json.toJson(0)
            )
          ),
          evt,
          parent
        )
      case evt: RetryScheduled =>
        updateVerbose(
          Json.obj(
            "$set" -> Json.obj(
              "runAt" -> Json.toJson(evt.at),
              "status" -> Json.toJson(WorkItemStatus.ScheduledForRetry)
            ),
            "$inc" -> Json.obj("retryCount" -> 1)
          ),
          evt,
          parent
        )
      case evt: Rescheduled =>
        updateVerbose(
          Json.obj(
            "$set" -> Json.obj(
              "payload" -> Json.toJsObject(evt.newPayload),
              "finishedProcessingAt" -> Json.toJson(evt.at),
              "retryCount" -> Json.toJson(0),
              "status" -> Json.toJson(WorkItemStatus.Scheduled),
              "runAt" -> Json.toJson(evt.newRunAt)
            )
          ),
          evt,
          parent
        )
      case evt: MaxRetriesReached =>
        updateVerbose(
          Json.obj(
            "$set" -> Json.obj(
              "errorStoppedProcessingAt" -> Json.toJson(Instant.now),
              "status" -> Json.toJson(WorkItemStatus.Error)
            )
          ),
          evt,
          parent
        )
    }
}

class MongoWorkItems[Payload: OFormat](
  protected val workload: Workload[Payload, _, _]
)(implicit executionContext: ExecutionContext,
  val mongoContext: MongoContext with WorkloadContext)
    extends ReactiveMongoDomainObjectGroup[EventId, WorkItem[Payload], WorkItemDoc[
      Payload
    ]]
    with WorkItemDescriptor[Payload]
    with WorkItems[Payload] {

  override def initialize: Future[Boolean] =
    for {
      coll <- objCollectionFt
      ensureIndexes <- ensureIndexes(coll)
    } yield ensureIndexes

  override def nextRunnablePage(size: Int): Future[List[WorkItem[Payload]]] =
    getListByJsonCrit(
      constraint = Json.obj("runAt" -> Json.obj("$exists" -> true)),
      sort = Json.obj("runAt" -> 1),
      limit = size
    )

  override def nextRunnable: Future[Option[WorkItem[Payload]]] =
    for {
      coll <- objCollectionFt
      nextUp <- coll
        .findAndUpdate(
          selector = Json.obj("runAt" -> Json.obj("$lt" -> Instant.now())),
          update = Json.obj("$unset" -> Json.obj("runAt" -> 1)),
          sort = Some(Json.obj("runAt" -> 1))
        )
        .map(_.value)
        .map(_.map(js => Json.fromJson[WorkItemDoc[Payload]](js)))
        .map {
          case Some(JsSuccess(doc, _)) => Some(cons(Right(doc)))
          case _                       => None
        }
    } yield nextUp

  override def nextRunAt: Future[Option[Instant]] =
    getListByJsonCrit(
      constraint = Json.obj("runAt" -> Json.obj("$exists" -> true)),
      sort = Json.obj("runAt" -> 1),
      limit = 1
    ).map(_.headOption.flatMap(_.runAt))

  override protected def ensureIndexes(coll: JSONCollection) = {
    val uniqueIndexKeys = workload.metadata.payloadUniqueConstraint
      .map(fld => s"payload.${fld}" -> IndexType.Ascending)
    for {
      // enforce uniqueness over all payloads in this collection
      payloadUnique <- coll.indexesManager.ensure(
        ObjectDescriptor
          .index(key = uniqueIndexKeys, unique = true, sparse = true)
      )
      // sparse index on runAt to speed up the retrieval of workItems that are ready to run
      runAt <- coll.indexesManager.ensure(
        ObjectDescriptor
          .index(key = Seq("runAt" -> IndexType.Ascending), sparse = true)
      )
    } yield payloadUnique && runAt
  }

  override val pFmt = implicitly[OFormat[Payload]]
  override protected def listConstraint: JsObject = Json.obj()

  override def list(
    q: DomainObjectGroup.Query
  ): Future[List[WorkItem[Payload]]] = q match {
    case WorkItems.RunnableAt(when) =>
      getListByJsonCrit(Json.obj("runAt" -> Json.obj("$lt" -> when)))
  }

  /**
    * @return Eventually returns a list of items whose runAt is in the past.
    */
  override def runnableItemSource(runAt: Instant, batchSize: Int)(
    implicit m: Materializer
  ): Future[Source[WorkItem[Payload], Any]] = {
    val q = Json.obj("runAt" -> Json.obj("$lt" -> Json.toJson(Instant.now)))
    for {
      coll <- objCollectionFt
    } yield {
      coll
        .find(q ++ notDeletedConstraint, projection = Some(Json.obj()))
        .batchSize(100)
        .sort(Json.obj("runAt" -> 1))
        .cursor[WorkItemDoc[Payload]]()
        .documentSource()
        .map(doc => cons(Right(doc)))
    }
  }

  override def apply(
    event: AllowedEvent,
    parent: Option[BaseEvent[EventId]]
  ): Future[WorkItem[Payload]] = event match {
    case evt: WorkItemAdded =>
      create(
        WorkItemDoc(
          _id = WorkItemId(),
          workloadId = workload.id,
          runAt = evt.runAt,
          payload = evt.payload,
          status = Some(Scheduled),
        ),
        evt,
        parent
      )
  }
}
