package twita.whipsaw.impl.inMemory

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.libs.json.OFormat
import twita.dominion.api.BaseEvent
import twita.dominion.api.DomainObjectGroup
import twita.dominion.api.DomainObjectGroup.byId
import twita.whipsaw.api.workloads.EventId
import twita.whipsaw.api.workloads.Metadata
import twita.whipsaw.api.workloads.ProcessingStatus
import twita.whipsaw.api.workloads.Processor
import twita.whipsaw.api.workloads.Scheduler
import twita.whipsaw.api.workloads.SchedulingStatus
import twita.whipsaw.api.workloads.WorkItems
import twita.whipsaw.api.workloads.Workload
import twita.whipsaw.api.workloads.WorkloadFactory
import twita.whipsaw.api.workloads.WorkloadId

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class WorkloadDoc[SParams, PParams] (
    _id: WorkloadId
  , name: String
  , factoryType: String
  , schedulerParams: SParams
  , processorParams: PParams
  , schedulingStatus: SchedulingStatus = SchedulingStatus.Init
  , processingStatus: ProcessingStatus = ProcessingStatus.Init
)

trait WorkloadDescriptor[Payload, SParams, PParams]
{
  def metadata: Metadata[Payload, SParams, PParams]

  protected def data: mutable.Map[WorkloadId, WorkloadDoc[SParams, PParams]]

  protected def cons: WorkloadDoc[SParams, PParams] => Workload[Payload, SParams, PParams] = {
    o => new InMemoryWorkload[Payload, SParams, PParams](metadata, o, data)
  }
}

class InMemoryWorkload[Payload, SParams, PParams](
    val metadata: Metadata[Payload, SParams, PParams]
  , protected val underlying: WorkloadDoc[SParams, PParams]
  , protected val data: mutable.Map[WorkloadId, WorkloadDoc[SParams, PParams]]
)(implicit executionContext: ExecutionContext)
  extends WorkloadDescriptor[Payload, SParams, PParams]
  with Workload[Payload, SParams, PParams]
{
  override def name: String = underlying.name

  override def workItems: WorkItems[Payload] = new InMemoryWorkItems[Payload](this)

  override def scheduler: Scheduler[Payload] = metadata.scheduler(underlying.schedulerParams)

  override def processor: Processor[Payload] = metadata.processor(underlying.processorParams)

  override def schedulingStatus: SchedulingStatus = underlying.schedulingStatus

  override def processingStatus: ProcessingStatus = underlying.processingStatus

  override def apply(
      event: AllowedEvent
    , parent: Option[BaseEvent[EventId]]
  ) : Future[Workload[Payload, SParams, PParams]] = event match {
    case evt: ScheduleStatusUpdated =>
      val result = underlying.copy(schedulingStatus = evt.schedulingStatus)
      data.put(underlying._id, result)
      Future.successful(cons(result))

    case evt: ProcessingStatusUpdated =>
      val result = underlying.copy(processingStatus = evt.processingStatus)
      data.put(underlying._id, result)
      Future.successful(cons(result))
  }
}

class InMemoryWorkloadFactory[Payload, SParams, PParams](
    val metadata: Metadata[Payload, SParams, PParams]
)(implicit executionContext: ExecutionContext)
  extends WorkloadDescriptor[Payload, SParams, PParams]
  with WorkloadFactory[Payload, SParams, PParams]
{
  protected val data = mutable.Map[WorkloadId, WorkloadDoc[SParams, PParams]]()

  override def list(q: DomainObjectGroup.Query): Future[List[Workload[Payload, SParams, PParams]]] = ???

  override def apply(event: AllowedEvent, parent: Option[BaseEvent[EventId]]): Future[Workload[Payload, SParams, PParams]] = event match {
    case evt: Created =>
      val result = WorkloadDoc(
          _id = WorkloadId()
        , name = evt.name
        , factoryType = metadata.factoryType
        , schedulerParams = evt.schedulerParams
        , processorParams = evt.processorParams
      )
      data.put(result._id, result)
      Future.successful(cons(result))
    }

  override implicit def spFmt: OFormat[SParams] = ???

  override implicit def ppFmt: OFormat[PParams] = ???

  override def get(q: DomainObjectGroup.Query): Future[Option[Workload[Payload, SParams, PParams]]] = q match {
    case q: byId[WorkloadId] => Future.successful(data.get(q.id).map(cons(_)))
  }

  override def list: Future[List[Workload[Payload, SParams, PParams]]] = ???

  override def source()(implicit m: Materializer): Future[Source[Workload[Payload, SParams, PParams], Any]] = ???

  override def count: Future[Long] = Future.successful(data.size.toLong)
}
