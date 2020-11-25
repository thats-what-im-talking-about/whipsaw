package twita.whipsaw.impl.inMemory

import java.time.Instant

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.WorkItem
import twita.whipsaw.api.workloads.WorkItemId
import twita.whipsaw.api.workloads.WorkItems
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadId

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkItemDoc[Payload] (
    _id: WorkItemId
  , workloadId: WorkloadId
  , payload: Payload
  , runAt: Option[Instant] = None
  , retryCount: Int = 0
  , startedProcessingAt: Option[Instant] = None
  , finishedProcessingAt: Option[Instant] = None
  , errorStoppedProcessingAt: Option[Instant] = None
)

trait WorkItemDescriptor[Payload] {
  protected def data: mutable.Map[WorkItemId, WorkItemDoc[Payload]]
  protected def workload: Workload[Payload, _, _]
  protected def cons: WorkItemDoc[Payload] => WorkItem[Payload] = o => new InMemoryWorkItem(o, workload, data)
}

class InMemoryWorkItem[Payload: OFormat](
    protected val underlying: WorkItemDoc[Payload]
  , protected val workload: Workload[Payload, _, _]
  , protected val data: mutable.Map[WorkItemId, WorkItemDoc[Payload]]
)(implicit executionContext: ExecutionContext)
  extends WorkItemDescriptor[Payload]
  with WorkItem[Payload]
{
  override def runAt: Option[Instant] = underlying.runAt
  override def payload: Payload = underlying.payload
  override def retryCount: Int = underlying.retryCount

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]) = event match {
    case evt: StartedProcessing =>
      val result = underlying.copy(
          startedProcessingAt = Some(evt.at)
        , runAt = None
        , finishedProcessingAt = None
      )
      data.put(underlying._id, result)
      Future.successful(cons(result))
    case evt: FinishedProcessing =>
      val result = underlying.copy(
          payload = evt.newPayload
        , finishedProcessingAt = Some(evt.at)
        , retryCount = 0
      )
      data.put(underlying._id, result)
      Future.successful(cons(result))
    case evt: RetryScheduled =>
      val result = underlying.copy(
          runAt = Some(evt.at)
        , retryCount = underlying.retryCount + 1
      )
      data.put(underlying._id, result)
      Future.successful(cons(result))
    case evt: MaxRetriesReached =>
      val result = underlying.copy(
        errorStoppedProcessingAt = Some(Instant.now)
      )
      data.put(underlying._id, result)
      Future.successful(cons(result))
  }
}

class InMemoryWorkItems[Payload: OFormat](protected val workload: Workload[Payload, _, _])(implicit executionContext: ExecutionContext)
  extends WorkItemDescriptor[Payload]
  with WorkItems[Payload]
{
  override def list(q: DomainObjectGroup.Query): Future[List[WorkItem[Payload]]] = q match {
    case WorkItems.RunnableAt(when) => getListByJsonCrit(Json.obj("runAt" -> Json.obj("$lt" -> when)))
  }

  /**
    * @return Eventually returns a list of items whose runAt is in the past.
    */
  override def runnableItemSource(runAt: Instant, batchSize: Int)(implicit m: Materializer): Future[Source[WorkItem[Payload], Any]] = {
    val q = Json.obj("runAt" -> Json.obj("$lt" -> Json.toJson(Instant.now)))
    for {
      coll <- objCollectionFt
    } yield {
      coll.find(q ++ notDeletedConstraint, projection = Some(Json.obj()))
        .batchSize(100)
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
